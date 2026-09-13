#![cfg_attr(windows, windows_subsystem = "windows")]

mod app_bootstrap;
mod commands;
mod engine;

fn main() {
    app_bootstrap::run();
}
