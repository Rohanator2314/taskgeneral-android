# ADR 001: Architecture Decision - UniFFI and Kotlin/Compose

Date: 2026-03-08

## Status

Accepted

## Context

The taskgeneral-android project requires a modern, performant, and maintainable architecture that bridges high-performance logic with a native Android user experience. The core Taskwarrior logic is best implemented in Rust for safety and performance, while the UI needs to feel at home on the Android platform.

We evaluated several architectural approaches for building this application:

1.  **UniFFI + Kotlin/Compose Multiplatform**: Using Rust for core logic with UniFFI generating bindings for Kotlin, and Jetpack Compose for the UI.
2.  **Slint**: A declarative UI toolkit for Rust.
3.  **Dioxus**: A portable UI framework for Rust using web technologies or native renderers.
4.  **Flutter + Rust**: Using Flutter for the UI and Rust for logic via FFI.

### Alternatives Evaluated

*   **Slint/Dioxus**: While these provide a "pure Rust" experience, their ecosystem for mobile-specific features (like system integrations, deep linking, and complex Android-specific UI patterns) is less mature than native Android tooling.
*   **Flutter**: Flutter offers great cross-platform capabilities, but adds another heavy layer (Dart VM) between the UI and the Rust core. It also lacks the seamless integration with the Android ecosystem that Kotlin provides.

## Decision

We have decided to use **UniFFI** to bridge a **Rust core** with a **Kotlin/Compose** frontend.

This combination allows us to:
*   Write performance-critical and platform-independent logic in Rust.
*   Automatically generate safe, idiomatic Kotlin bindings using UniFFI.
*   Build a modern, reactive UI using Jetpack Compose, the standard for modern Android development.
*   Access the full Android ecosystem and platform APIs directly from Kotlin.

## Consequences

### Positive
*   **Performance**: Rust provides high performance for task processing and synchronization.
*   **Safety**: Rust's memory safety guarantees reduce crashes in the core logic.
*   **Developer Experience**: Jetpack Compose offers a superior UI development experience on Android compared to current Rust-native UI toolkits.
*   **Production Readiness**: Both Kotlin/Compose and UniFFI are proven in production environments for mobile applications.

### Negative
*   **Complexity**: Maintaining a cross-language boundary requires careful management of the build system (e.g., Cargo + Gradle).
*   **Binding Overhead**: There is a small performance cost when crossing the FFI boundary, though minimal for most use cases.
*   **Tooling**: Developers need to be familiar with both the Rust and Android/Kotlin toolchains.
