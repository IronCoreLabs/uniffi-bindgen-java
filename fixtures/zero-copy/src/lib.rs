/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

uniffi::setup_scaffolding!("zero_copy");

/// Borrowed bytes: crosses the FFI as `ForeignBytes` (pointer + length), no copy.
#[uniffi::export]
fn checksum_borrowed(data: &[u8]) -> u64 {
    data.iter().map(|b| *b as u64).sum()
}

/// Same work over owned bytes, which copies through a `RustBuffer`. The baseline to measure
/// `checksum_borrowed` against.
#[uniffi::export]
fn checksum_owned(data: Vec<u8>) -> u64 {
    data.iter().map(|b| *b as u64).sum()
}

/// Proves the borrow really is the foreign buffer rather than a copy: the first byte is
/// reported back, so a caller can mutate its buffer between calls and observe the change.
#[uniffi::export]
fn first_byte_borrowed(data: &[u8]) -> u8 {
    data.first().copied().unwrap_or(0)
}

#[uniffi::export]
fn len_borrowed(data: &[u8]) -> u32 {
    data.len() as u32
}

// An `async fn` taking `&[u8]` does not compile: `ForeignBytes` holds a `*const u8`, so the
// generated future isn't `Send` and fails `rust_future_new`'s bound. Zero-copy is sync-only,
// and enforced by rustc rather than by us.
