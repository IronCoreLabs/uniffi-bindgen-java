/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.primitive_arrays.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestPrimitiveArrays {
    public static void main(String[] args) {
        testFloat32Arrays();
        testFloat64Arrays();
        testInt16Arrays();
        testInt32Arrays();
        testInt64Arrays();
        testBooleanArrays();
        testUnsignedArrays();
        testEmptyArrays();
        testLargeArrays();
        testHashedPositions();
        testNestedHashedPositions();
        testValueEquality();

        System.out.println("All primitive array tests passed!");
    }

    static void testNestedHashedPositions() {
        Set<List<List<Integer>>> nested = new HashSet<>(List.of(
            List.of(List.of(1, 2), List.of(3)),
            List.of(List.of(4))));
        Set<List<List<Integer>>> nestedResult = PrimitiveArrays.roundtripNestedInt32Set(nested);
        assert nestedResult.equals(nested) : "Set<List<List<Integer>>> roundtrip failed";
        assert nestedResult.contains(List.of(List.of(1, 2), List.of(3)))
            : "nested contains should match by value";

        Set<List<Integer>> optional = new HashSet<>();
        optional.add(List.of(1, 2, 3));
        optional.add(null);
        Set<List<Integer>> optionalResult = PrimitiveArrays.roundtripOptionalInt32Set(optional);
        assert optionalResult.equals(optional) : "Set with an absent element roundtrip failed";
        assert optionalResult.contains(List.of(1, 2, 3)) : "optional contains should match by value";
        assert optionalResult.contains(null) : "the absent element should survive the roundtrip";
    }

    static void testValueEquality() {
        // Guards identity-based equals/hashCode on generated types holding arrays, which would
        // let value-equal instances coexist in sets and never match on contains.
        IntsHolder a1 = holder();
        IntsHolder a2 = holder();
        assert a1.equals(a2) : "holders with equal arrays should be equal";
        assert a1.hashCode() == a2.hashCode() : "equal holders should hash alike";

        Set<IntsHolder> holders = new HashSet<>(List.of(a1, a2));
        assert holders.size() == 1 : "value-equal holders should collapse";
        Set<IntsHolder> holderResult = PrimitiveArrays.roundtripHolderSet(holders);
        assert holderResult.equals(holders) : "Set<IntsHolder> roundtrip failed";
        assert holderResult.contains(holder()) : "contains should match holders by value";

        IntsEnum ints = new IntsEnum.Ints(new int[] { 5, 6 });
        assert ints.equals(new IntsEnum.Ints(new int[] { 5, 6 }))
            : "variants with equal arrays should be equal";
        assert ints.hashCode() == new IntsEnum.Ints(new int[] { 5, 6 }).hashCode()
            : "equal variants should hash alike";
        assert PrimitiveArrays.roundtripIntsEnum(ints).equals(ints) : "IntsEnum roundtrip failed";
        assert !ints.equals(new IntsEnum.Empty()) : "different variants should not be equal";

        IntsKey k1 = new IntsKey(new int[] { 7, 8 });
        assert k1.equals(new IntsKey(new int[] { 7, 8 }))
            : "custom wrappers with equal arrays should be equal";
        Set<IntsKey> keys = new HashSet<>(List.of(k1, new IntsKey(new int[] { 7, 8 })));
        assert keys.size() == 1 : "value-equal custom wrappers should collapse";
        Set<IntsKey> keyResult = PrimitiveArrays.roundtripKeySet(keys);
        assert keyResult.equals(keys) : "Set<IntsKey> roundtrip failed";
        assert keyResult.contains(new IntsKey(new int[] { 7, 8 }))
            : "contains should match custom wrappers by value";
    }

    static IntsHolder holder() {
        return new IntsHolder("a", new int[] { 1, 2 }, List.of(new int[] { 3, 4 }));
    }

    static void testHashedPositions() {
        // Guards the `int[]` lens being applied here too, which compiles but never matches on
        // lookup and lets Java hold duplicates Rust collapsed.
        Set<List<Integer>> set = new HashSet<>(List.of(List.of(1, 2, 3), List.of(4, 5)));
        Set<List<Integer>> setResult = PrimitiveArrays.roundtripInt32Set(set);
        assert setResult.equals(set) : "Set<List<Integer>> roundtrip failed";
        assert setResult.contains(List.of(1, 2, 3)) : "contains should match by value";

        Set<List<Integer>> duplicated = new HashSet<>(
            List.of(List.of(1, 2, 3), new ArrayList<>(List.of(1, 2, 3))));
        assert duplicated.size() == 1 : "value-equal lists should already collapse in Java";

        Map<List<Integer>, String> map = new HashMap<>();
        map.put(List.of(1, 2, 3), "first");
        map.put(List.of(4, 5), "second");
        Map<List<Integer>, String> mapResult = PrimitiveArrays.roundtripInt32KeyedMap(map);
        assert mapResult.equals(map) : "Map<List<Integer>, String> roundtrip failed";
        assert "first".equals(mapResult.get(List.of(1, 2, 3))) : "get should match by value";

        // A map *value* is never hashed, so it keeps the double[] rendering.
        Map<String, double[]> valued = new HashMap<>();
        valued.put("a", new double[] { 1.5, 2.5 });
        Map<String, double[]> valuedResult = PrimitiveArrays.roundtripFloat64ValuedMap(valued);
        assert Arrays.equals(valuedResult.get("a"), new double[] { 1.5, 2.5 })
            : "double[] map value roundtrip failed";
    }

    static void testFloat32Arrays() {
        // Test float[] roundtrip
        float[] floats = new float[] { 1.0f, 2.5f, 3.14159f, -0.5f, Float.MAX_VALUE, Float.MIN_VALUE };
        float[] floatResult = PrimitiveArrays.roundtripFloat32(floats);
        assert Arrays.equals(floats, floatResult) : "float[] roundtrip failed";

        // Test sum
        float[] sumInput = new float[] { 1.0f, 2.0f, 3.0f, 4.0f };
        float sum = PrimitiveArrays.sumFloat32(sumInput);
        assert sum == 10.0f : "sumFloat32 failed: expected 10.0, got " + sum;
    }

    static void testFloat64Arrays() {
        // Test double[] roundtrip
        double[] doubles = new double[] { 1.0, 2.5, 3.14159265359, -0.5, Double.MAX_VALUE, Double.MIN_VALUE };
        double[] doubleResult = PrimitiveArrays.roundtripFloat64(doubles);
        assert Arrays.equals(doubles, doubleResult) : "double[] roundtrip failed";

        // Test sum
        double[] sumInput = new double[] { 1.0, 2.0, 3.0, 4.0, 5.0 };
        double sum = PrimitiveArrays.sumFloat64(sumInput);
        assert sum == 15.0 : "sumFloat64 failed: expected 15.0, got " + sum;
    }

    static void testInt16Arrays() {
        // Test short[] roundtrip
        short[] shorts = new short[] { -1, 0, 1, Short.MAX_VALUE, Short.MIN_VALUE, 12345 };
        short[] shortResult = PrimitiveArrays.roundtripInt16(shorts);
        assert Arrays.equals(shorts, shortResult) : "short[] roundtrip failed";

        // Test sum
        short[] sumInput = new short[] { 1, 2, 3, 4, 5 };
        short sum = PrimitiveArrays.sumInt16(sumInput);
        assert sum == 15 : "sumInt16 failed: expected 15, got " + sum;
    }

    static void testInt32Arrays() {
        // Test int[] roundtrip
        int[] ints = new int[] { -1, 0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE, 123456789 };
        int[] intResult = PrimitiveArrays.roundtripInt32(ints);
        assert Arrays.equals(ints, intResult) : "int[] roundtrip failed";

        // Test sum
        int[] sumInput = new int[] { 1, 2, 3, 4, 5 };
        int sum = PrimitiveArrays.sumInt32(sumInput);
        assert sum == 15 : "sumInt32 failed: expected 15, got " + sum;
    }

    static void testInt64Arrays() {
        // Test long[] roundtrip
        long[] longs = new long[] { -1L, 0L, 1L, Long.MAX_VALUE, Long.MIN_VALUE, 123456789012345L };
        long[] longResult = PrimitiveArrays.roundtripInt64(longs);
        assert Arrays.equals(longs, longResult) : "long[] roundtrip failed";

        // Test sum
        long[] sumInput = new long[] { 1L, 2L, 3L, 4L, 5L };
        long sum = PrimitiveArrays.sumInt64(sumInput);
        assert sum == 15L : "sumInt64 failed: expected 15, got " + sum;
    }

    static void testBooleanArrays() {
        // Test boolean[] roundtrip
        boolean[] bools = new boolean[] { true, false, true, true, false, false, true };
        boolean[] boolResult = PrimitiveArrays.roundtripBool(bools);
        assert Arrays.equals(bools, boolResult) : "boolean[] roundtrip failed";

        // Test count_true
        int trueCount = PrimitiveArrays.countTrue(bools);
        assert trueCount == 4 : "countTrue failed: expected 4, got " + trueCount;

        // Test all false
        boolean[] allFalse = new boolean[] { false, false, false };
        assert PrimitiveArrays.countTrue(allFalse) == 0 : "countTrue for all false failed";

        // Test all true
        boolean[] allTrue = new boolean[] { true, true, true };
        assert PrimitiveArrays.countTrue(allTrue) == 3 : "countTrue for all true failed";
    }

    static void testUnsignedArrays() {
        // Test unsigned short[] (u16) - values that would be negative as signed
        short[] uint16Values = new short[] { 0, 1, (short) 32767, (short) 32768, (short) 65535 };
        short[] uint16Result = PrimitiveArrays.roundtripUint16(uint16Values);
        assert Arrays.equals(uint16Values, uint16Result) : "u16 as short[] roundtrip failed";

        // Test unsigned int[] (u32) - values that would be negative as signed
        int[] uint32Values = new int[] { 0, 1, Integer.MAX_VALUE, Integer.MAX_VALUE + 1, -1 };
        int[] uint32Result = PrimitiveArrays.roundtripUint32(uint32Values);
        assert Arrays.equals(uint32Values, uint32Result) : "u32 as int[] roundtrip failed";

        // Test unsigned long[] (u64) - values that would be negative as signed
        long[] uint64Values = new long[] { 0L, 1L, Long.MAX_VALUE, Long.MAX_VALUE + 1, -1L };
        long[] uint64Result = PrimitiveArrays.roundtripUint64(uint64Values);
        assert Arrays.equals(uint64Values, uint64Result) : "u64 as long[] roundtrip failed";
    }

    static void testEmptyArrays() {
        // Test empty arrays for all types
        assert PrimitiveArrays.roundtripFloat32(new float[0]).length == 0 : "empty float[] roundtrip failed";
        assert PrimitiveArrays.roundtripFloat64(new double[0]).length == 0 : "empty double[] roundtrip failed";
        assert PrimitiveArrays.roundtripInt16(new short[0]).length == 0 : "empty short[] roundtrip failed";
        assert PrimitiveArrays.roundtripInt32(new int[0]).length == 0 : "empty int[] roundtrip failed";
        assert PrimitiveArrays.roundtripInt64(new long[0]).length == 0 : "empty long[] roundtrip failed";
        assert PrimitiveArrays.roundtripBool(new boolean[0]).length == 0 : "empty boolean[] roundtrip failed";

        // Test sums on empty arrays
        assert PrimitiveArrays.sumFloat32(new float[0]) == 0.0f : "sumFloat32 on empty array failed";
        assert PrimitiveArrays.sumFloat64(new double[0]) == 0.0 : "sumFloat64 on empty array failed";
        assert PrimitiveArrays.sumInt32(new int[0]) == 0 : "sumInt32 on empty array failed";
        assert PrimitiveArrays.countTrue(new boolean[0]) == 0 : "countTrue on empty array failed";
    }

    static void testLargeArrays() {
        // Test large arrays (10,000 elements) for performance sanity
        int size = 10000;

        // Float32
        float[] largeFloats = new float[size];
        for (int i = 0; i < size; i++) {
            largeFloats[i] = (float) i * 0.1f;
        }
        float[] largeFloatResult = PrimitiveArrays.roundtripFloat32(largeFloats);
        assert Arrays.equals(largeFloats, largeFloatResult) : "large float[] roundtrip failed";

        // Int32
        int[] largeInts = new int[size];
        for (int i = 0; i < size; i++) {
            largeInts[i] = i;
        }
        int[] largeIntResult = PrimitiveArrays.roundtripInt32(largeInts);
        assert Arrays.equals(largeInts, largeIntResult) : "large int[] roundtrip failed";

        // Boolean
        boolean[] largeBools = new boolean[size];
        for (int i = 0; i < size; i++) {
            largeBools[i] = i % 2 == 0;
        }
        boolean[] largeBoolResult = PrimitiveArrays.roundtripBool(largeBools);
        assert Arrays.equals(largeBools, largeBoolResult) : "large boolean[] roundtrip failed";
        assert PrimitiveArrays.countTrue(largeBools) == size / 2 : "countTrue on large array failed";
    }
}
