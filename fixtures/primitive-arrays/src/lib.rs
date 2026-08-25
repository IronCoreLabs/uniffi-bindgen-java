/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use std::collections::{HashMap, HashSet};

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

// Hashed positions, where Java has to keep the boxed `List` rendering to preserve value equality.

#[uniffi::export]
fn roundtrip_int32_set(data: HashSet<Vec<i32>>) -> HashSet<Vec<i32>> {
    data
}

#[uniffi::export]
fn roundtrip_int32_keyed_map(data: HashMap<Vec<i32>, String>) -> HashMap<Vec<i32>, String> {
    data
}

/// Values are not hashed, so `Vec<f64>` keeps the `double[]` rendering here.
#[uniffi::export]
fn roundtrip_float64_valued_map(data: HashMap<String, Vec<f64>>) -> HashMap<String, Vec<f64>> {
    data
}

// Hashed positions reached through nesting, which must render boxed at every depth.

#[uniffi::export]
fn roundtrip_nested_int32_set(data: HashSet<Vec<Vec<i32>>>) -> HashSet<Vec<Vec<i32>>> {
    data
}

#[uniffi::export]
fn roundtrip_optional_int32_set(data: HashSet<Option<Vec<i32>>>) -> HashSet<Option<Vec<i32>>> {
    data
}

// Array-holding fields, whose Java equals/hashCode must compare by value.

#[derive(uniffi::Record, PartialEq, Eq, Hash)]
pub struct IntsHolder {
    pub label: String,
    pub data: Vec<i32>,
    pub nested: Vec<Vec<i32>>,
}

#[uniffi::export]
fn roundtrip_holder_set(data: HashSet<IntsHolder>) -> HashSet<IntsHolder> {
    data
}

#[derive(uniffi::Enum)]
pub enum IntsEnum {
    Empty,
    Ints { values: Vec<i32> },
}

#[uniffi::export]
fn roundtrip_ints_enum(data: IntsEnum) -> IntsEnum {
    data
}

/// `f64` keeps Java's `==` out of the generated equals: NaN fields must stay reflexively equal.
#[derive(uniffi::Record, PartialEq)]
pub struct FloatHolder {
    pub ratio: f64,
    pub data: Vec<i32>,
}

#[uniffi::export]
fn roundtrip_float_holder(data: FloatHolder) -> FloatHolder {
    data
}

/// A custom newtype over an array-rendering builtin; its Java wrapper record must also compare
/// by value.
#[derive(PartialEq, Eq, Hash)]
pub struct IntsKey(pub Vec<i32>);
uniffi::custom_newtype!(IntsKey, Vec<i32>);

#[uniffi::export]
fn roundtrip_key_set(data: HashSet<IntsKey>) -> HashSet<IntsKey> {
    data
}
