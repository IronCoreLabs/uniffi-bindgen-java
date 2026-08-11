/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

//! Borrowed `&[u8]` arguments, which cross the FFI as `ForeignBytes` (pointer + length) rather
//! than being copied through a `RustBuffer`.

uniffi::setup_scaffolding!("byref_bytes");

#[uniffi::export]
fn sum_borrowed(data: &[u8]) -> u32 {
    data.iter().map(|b| *b as u32).sum()
}

#[uniffi::export]
fn len_borrowed(data: &[u8]) -> u32 {
    data.len() as u32
}

/// Copies the borrow, proving the bytes are readable rather than merely correctly sized.
#[uniffi::export]
fn copy_borrowed(data: &[u8]) -> Vec<u8> {
    data.to_vec()
}

/// A borrowed and an owned `bytes` in one signature: the two take different FFI paths and the
/// argument order must survive that.
#[uniffi::export]
fn concat_borrowed_and_owned(borrowed: &[u8], owned: Vec<u8>) -> Vec<u8> {
    [borrowed, &owned].concat()
}
