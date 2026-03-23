/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

uniffi::setup_scaffolding!("primitive_arrays");

// Float32 (float[]) operations
#[uniffi::export]
fn roundtrip_float32(data: Vec<f32>) -> Vec<f32> {
    data
}

#[uniffi::export]
fn sum_float32(data: Vec<f32>) -> f32 {
    data.iter().sum()
}

// Float64 (double[]) operations
#[uniffi::export]
fn roundtrip_float64(data: Vec<f64>) -> Vec<f64> {
    data
}

#[uniffi::export]
fn sum_float64(data: Vec<f64>) -> f64 {
    data.iter().sum()
}

// Int16 (short[]) operations
#[uniffi::export]
fn roundtrip_int16(data: Vec<i16>) -> Vec<i16> {
    data
}

#[uniffi::export]
fn sum_int16(data: Vec<i16>) -> i16 {
    data.iter().sum()
}

// Int32 (int[]) operations
#[uniffi::export]
fn roundtrip_int32(data: Vec<i32>) -> Vec<i32> {
    data
}

#[uniffi::export]
fn sum_int32(data: Vec<i32>) -> i32 {
    data.iter().sum()
}

// Int64 (long[]) operations
#[uniffi::export]
fn roundtrip_int64(data: Vec<i64>) -> Vec<i64> {
    data
}

#[uniffi::export]
fn sum_int64(data: Vec<i64>) -> i64 {
    data.iter().sum()
}

// Boolean (boolean[]) operations
#[uniffi::export]
fn roundtrip_bool(data: Vec<bool>) -> Vec<bool> {
    data
}

#[uniffi::export]
fn count_true(data: Vec<bool>) -> i32 {
    data.iter().filter(|&&b| b).count() as i32
}

// UInt variants to test unsigned handling
#[uniffi::export]
fn roundtrip_uint16(data: Vec<u16>) -> Vec<u16> {
    data
}

#[uniffi::export]
fn roundtrip_uint32(data: Vec<u32>) -> Vec<u32> {
    data
}

#[uniffi::export]
fn roundtrip_uint64(data: Vec<u64>) -> Vec<u64> {
    data
}
