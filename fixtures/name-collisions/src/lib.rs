/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

//! User-chosen identifiers that equal names the Java templates introduce in the same scope.
//!
//! Absent here, because uniffi-rs's own Rust derives and FFI callback signatures already reject
//! them: a field named `buf`; callback-interface parameters named `call_status`,
//! `uniffi_call_status`, `uniffi_handle`, `uniffi_out_return`, `uniffi_future_callback`,
//! `uniffi_callback_data` or `uniffi_out_dropped_callback`.

use std::sync::Arc;

uniffi::setup_scaffolding!("name_collisions");

/// `value` is the converter's parameter; `v1`/`v2` are its positional pattern bindings.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum Shadowing {
    Unit,
    Named { value: String, v1: i32, v2: bool },
    Tuple(String, u64),
}

#[uniffi::export]
fn roundtrip_enum(value: Shadowing) -> Shadowing {
    value
}

#[uniffi::export]
fn enum_named_value(buf: Shadowing) -> Option<String> {
    match buf {
        Shadowing::Named { value, .. } => Some(value),
        _ => None,
    }
}

/// `x` is the error converter's type-pattern binding.
#[derive(Debug, Clone, PartialEq, thiserror::Error, uniffi::Error)]
pub enum ShadowError {
    #[error("named {value} {x}")]
    Named { value: String, x: i32 },
}

#[uniffi::export]
fn throw_named(value: String, x: i32) -> Result<(), ShadowError> {
    Err(ShadowError::Named { value, x })
}

/// Parameters named after the locals of the sync call path and the `UniffiLib` wrappers.
#[uniffi::export]
fn sync_fn(
    uniffi_out_err: String,
    status: String,
    allocator: String,
    it: String,
    e: String,
) -> String {
    format!("{uniffi_out_err}|{status}|{allocator}|{it}|{e}")
}

/// Parameters named after the locals of the async call path.
#[uniffi::export]
async fn async_fn(uniffi_executor: String, it: String, uniffi_result: String) -> String {
    format!("{uniffi_executor}|{it}|{uniffi_result}")
}

#[derive(uniffi::Object)]
pub struct Holder {
    tag: String,
}

#[uniffi::export]
impl Holder {
    #[uniffi::constructor]
    fn new(uniffi_handle: String) -> Arc<Self> {
        Arc::new(Self { tag: uniffi_handle })
    }

    fn sync_method(&self, uniffi_handle: String, uniffi_out_err: String) -> String {
        format!("{}|{uniffi_handle}|{uniffi_out_err}", self.tag)
    }

    async fn async_method(
        &self,
        uniffi_handle: String,
        uniffi_executor: String,
        it: String,
    ) -> String {
        format!("{}|{uniffi_handle}|{uniffi_executor}|{it}", self.tag)
    }
}

/// Parameters named after every local the callback-interface implementation class binds.
#[uniffi::export(with_foreign)]
#[async_trait::async_trait]
pub trait Shadow: Send + Sync {
    fn sync_value(
        &self,
        uniffi_obj: String,
        make_call: String,
        write_return: String,
        uniffi_value: String,
        out_return: String,
        lowered: String,
        status: String,
    ) -> String;

    fn sync_primitive(&self, uniffi_value: i32, out_return: i32) -> i32;

    fn sync_void(&self, nothing: String, status: String);

    fn sync_throws(&self, e: String, status: String) -> Result<String, ShadowError>;

    async fn async_value(
        &self,
        uniffi_completion_descriptor: String,
        uniffi_handle_success: String,
        return_value: String,
        uniffi_result: String,
        global_callback: String,
        mh: String,
        t: String,
        uniffi_handle_error: String,
        status: String,
        lowered: String,
    ) -> String;

    async fn async_primitive(&self, return_value: i32, uniffi_result: i32) -> i32;

    async fn async_void(&self, nothing: String, return_value: String);

    async fn async_throws(&self, e: String, t: String) -> Result<String, ShadowError>;
}

#[uniffi::export]
fn call_sync_value(shadow: Arc<dyn Shadow>, prefix: String) -> String {
    shadow.sync_value(
        format!("{prefix}obj"),
        format!("{prefix}call"),
        format!("{prefix}ret"),
        format!("{prefix}val"),
        format!("{prefix}out"),
        format!("{prefix}low"),
        format!("{prefix}status"),
    )
}

#[uniffi::export]
fn call_sync_primitive(shadow: Arc<dyn Shadow>, a: i32, b: i32) -> i32 {
    shadow.sync_primitive(a, b)
}

#[uniffi::export]
fn call_sync_void(shadow: Arc<dyn Shadow>, nothing: String) {
    shadow.sync_void(nothing, "status".to_string())
}

#[uniffi::export]
fn call_sync_throws(shadow: Arc<dyn Shadow>, e: String) -> Result<String, ShadowError> {
    shadow.sync_throws(e, "status".to_string())
}

#[uniffi::export]
async fn call_async_value(shadow: Arc<dyn Shadow>, prefix: String) -> String {
    shadow
        .async_value(
            format!("{prefix}desc"),
            format!("{prefix}success"),
            format!("{prefix}ret"),
            format!("{prefix}result"),
            format!("{prefix}global"),
            format!("{prefix}mh"),
            format!("{prefix}t"),
            format!("{prefix}error"),
            format!("{prefix}status"),
            format!("{prefix}low"),
        )
        .await
}

#[uniffi::export]
async fn call_async_primitive(shadow: Arc<dyn Shadow>, a: i32, b: i32) -> i32 {
    shadow.async_primitive(a, b).await
}

#[uniffi::export]
async fn call_async_void(shadow: Arc<dyn Shadow>, nothing: String) {
    shadow.async_void(nothing, "ret".to_string()).await
}

#[uniffi::export]
async fn call_async_throws(shadow: Arc<dyn Shadow>, e: String) -> Result<String, ShadowError> {
    shadow.async_throws(e, "t".to_string()).await
}
