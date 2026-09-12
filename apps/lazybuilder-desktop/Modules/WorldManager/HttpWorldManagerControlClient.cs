using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.WorldManager;

public sealed class HttpWorldManagerControlClient : IWorldManagerControl, IDisposable
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private readonly HttpClient _httpClient;

    public HttpWorldManagerControlClient(WorldControlOptions options)
    {
        var normalized = options.EnsureToken();
        _httpClient = new HttpClient
        {
            BaseAddress = new Uri($"http://127.0.0.1:{normalized.Port}/"),
            Timeout = TimeSpan.FromSeconds(3)
        };
        _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", normalized.Token);
    }

    public async Task<WorldControlStatus> GetStatusAsync(CancellationToken cancellationToken = default)
    {
        using var response = await _httpClient.GetAsync("v1/status", cancellationToken);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<WorldControlStatus>(JsonOptions, cancellationToken)
            ?? throw new InvalidDataException("World-Manager returned an empty status response.");
    }

    public async Task<IReadOnlyList<ManagedWorldSummary>> ListWorldsAsync(CancellationToken cancellationToken = default)
    {
        using var response = await _httpClient.GetAsync("v1/worlds", cancellationToken);
        response.EnsureSuccessStatusCode();
        var payload = await response.Content.ReadFromJsonAsync<WorldListPayload>(JsonOptions, cancellationToken)
            ?? throw new InvalidDataException("World-Manager returned an empty world list response.");
        return payload.Worlds ?? Array.Empty<ManagedWorldSummary>();
    }

    public void Dispose() => _httpClient.Dispose();

    private sealed record WorldListPayload(IReadOnlyList<ManagedWorldSummary>? Worlds);
}
