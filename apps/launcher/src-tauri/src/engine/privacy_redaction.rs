use std::collections::BTreeSet;

#[derive(Clone, Debug)]
struct Replacement {
    value: String,
    marker: &'static str,
}

/// Canonical export-time redaction policy for diagnostic/support artifacts.
/// Local runtime logs remain local; anything copied into a support artifact must pass
/// through this policy so Windows paths are removed in raw, normalized and JSON-escaped form.
pub struct RedactionPolicy {
    replacements: Vec<Replacement>,
}

impl RedactionPolicy {
    pub fn for_support_bundle(sensitive_paths: &[String]) -> Self {
        let mut candidates: Vec<(String, &'static str)> = Vec::new();
        for (key, marker) in [
            ("LOCALAPPDATA", "<LOCALAPPDATA>"),
            ("APPDATA", "<APPDATA>"),
            ("USERPROFILE", "<USERPROFILE>"),
            ("HOME", "<HOME>"),
        ] {
            if let Some(value) = std::env::var_os(key).and_then(|value| value.into_string().ok()) {
                add_path_variants(&mut candidates, &value, marker);
            }
        }
        for value in sensitive_paths {
            add_path_variants(&mut candidates, value, "<WORKSPACE>");
        }

        // Longest values win so a concrete workspace below USERPROFILE is replaced as
        // <WORKSPACE> before the broader profile root can consume part of the path.
        candidates.sort_by(|left, right| right.0.len().cmp(&left.0.len()).then_with(|| left.0.cmp(&right.0)));
        let mut seen = BTreeSet::new();
        let replacements = candidates
            .into_iter()
            .filter(|(value, _)| seen.insert(value.to_ascii_lowercase()))
            .map(|(value, marker)| Replacement { value, marker })
            .collect();
        Self { replacements }
    }

    pub fn redact(&self, input: &str) -> String {
        let mut output = input.to_string();
        for replacement in &self.replacements {
            output = replace_case_insensitive(&output, &replacement.value, replacement.marker);
        }
        output
    }
}

fn add_path_variants(target: &mut Vec<(String, &'static str)>, value: &str, marker: &'static str) {
    let value = value.trim();
    if value.is_empty() {
        return;
    }
    target.push((value.to_string(), marker));

    let slash_normalized = value.replace('\\', "/");
    if slash_normalized != value {
        target.push((slash_normalized, marker));
    }

    // serde_json encodes each Windows path separator as `\\`. Strip only the JSON
    // string quotes so the resulting replacement matches paths inside serialized JSON.
    if let Ok(encoded) = serde_json::to_string(value) {
        if encoded.len() >= 2 {
            let escaped = encoded[1..encoded.len() - 1].to_string();
            if escaped != value {
                target.push((escaped, marker));
            }
        }
    }
}

fn replace_case_insensitive(input: &str, needle: &str, replacement: &str) -> String {
    if needle.is_empty() {
        return input.to_string();
    }
    let lower_input = input.to_ascii_lowercase();
    let lower_needle = needle.to_ascii_lowercase();
    let mut output = String::with_capacity(input.len());
    let mut cursor = 0usize;
    while let Some(relative) = lower_input[cursor..].find(&lower_needle) {
        let start = cursor + relative;
        output.push_str(&input[cursor..start]);
        output.push_str(replacement);
        cursor = start + needle.len();
    }
    output.push_str(&input[cursor..]);
    output
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn workspace_paths_are_redacted_case_insensitively() {
        let policy = RedactionPolicy::for_support_bundle(&["D:\\Servers\\Build".into()]);
        let value = policy.redact("d:\\servers\\build\\server\\paper.jar");
        assert!(!value.to_ascii_lowercase().contains("d:\\servers\\build"));
        assert!(value.contains("<WORKSPACE>"));
    }

    #[test]
    fn slash_normalized_workspace_paths_are_redacted() {
        let policy = RedactionPolicy::for_support_bundle(&["D:\\Servers\\Build".into()]);
        let value = policy.redact("D:/Servers/Build/server/paper.jar");
        assert!(!value.to_ascii_lowercase().contains("d:/servers/build"));
        assert!(value.contains("<WORKSPACE>"));
    }

    #[test]
    fn json_escaped_windows_paths_are_redacted() {
        let path = "D:\\Servers\\Build".to_string();
        let serialized = serde_json::to_string(&serde_json::json!({ "workspacePath": path })).unwrap();
        assert!(serialized.contains(r#"D:\\Servers\\Build"#));
        let policy = RedactionPolicy::for_support_bundle(&["D:\\Servers\\Build".into()]);
        let redacted = policy.redact(&serialized);
        assert!(!redacted.contains(r#"D:\\Servers\\Build"#));
        assert!(redacted.contains("<WORKSPACE>"));
    }
}
