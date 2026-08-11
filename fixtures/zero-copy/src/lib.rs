/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

//! Borrowed `&[u8]` arguments, which cross the FFI as `ForeignBytes` (pointer + length) instead of
//! being copied through a `RustBuffer`.
//!
//! There is deliberately no async function here: one taking `&[u8]` does not compile, because
//! `ForeignBytes` holds a `*const u8` so the generated future isn't `Send` and fails
//! `rust_future_new`'s bound.

uniffi::setup_scaffolding!("zero_copy");

#[uniffi::export]
fn checksum_borrowed(data: &[u8]) -> u64 {
    data.iter().map(|b| *b as u64).sum()
}

/// Baseline for `checksum_borrowed`.
#[uniffi::export]
fn checksum_owned(data: Vec<u8>) -> u64 {
    data.iter().map(|b| *b as u64).sum()
}

/// Lets a caller mutate its buffer between calls and see the change, which a copy would hide.
#[uniffi::export]
fn first_byte_borrowed(data: &[u8]) -> u8 {
    data.first().copied().unwrap_or(0)
}

#[uniffi::export]
fn len_borrowed(data: &[u8]) -> u32 {
    data.len() as u32
}

/// Argument order has to survive the two `bytes` taking different FFI paths.
#[uniffi::export]
fn concat_borrowed_and_owned(borrowed: &[u8], owned: Vec<u8>) -> Vec<u8> {
    [borrowed, &owned].concat()
}
