#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod app_bootstrap;
mod commands;
mod engine;

fn main() {
    app_bootstrap::run();
}
