/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import uniffi.name_collisions.*;

public class TestNameCollisions {
    static class JavaShadow implements Shadow {
        String lastVoid;
        String lastAsyncVoid;

        @Override
        public String syncValue(String uniffiObj, String makeCall, String writeReturn, String uniffiValue,
                                String outReturn, String lowered, String status) {
            return String.join(",", uniffiObj, makeCall, writeReturn, uniffiValue, outReturn, lowered, status);
        }

        @Override
        public int syncPrimitive(int uniffiValue, int outReturn) {
            return uniffiValue * 10 + outReturn;
        }

        @Override
        public void syncVoid(String nothing, String status) {
            lastVoid = nothing + "," + status;
        }

        @Override
        public String syncThrows(String e, String status) throws ShadowException {
            if (e.equals("throw")) {
                throw new ShadowException.Named("value", 7);
            }
            return e + "," + status;
        }

        @Override
        public CompletableFuture<String> asyncValue(String uniffiCompletionDescriptor, String uniffiHandleSuccess,
                                                    String returnValue, String uniffiResult, String globalCallback,
                                                    String mh, String t, String uniffiHandleError, String status,
                                                    String lowered) {
            return CompletableFuture.completedFuture(String.join(",", uniffiCompletionDescriptor, uniffiHandleSuccess,
                returnValue, uniffiResult, globalCallback, mh, t, uniffiHandleError, status, lowered));
        }

        @Override
        public CompletableFuture<Integer> asyncPrimitive(int returnValue, int uniffiResult) {
            return CompletableFuture.completedFuture(returnValue * 10 + uniffiResult);
        }

        @Override
        public CompletableFuture<Void> asyncVoid(String nothing, String returnValue) {
            lastAsyncVoid = nothing + "," + returnValue;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<String> asyncThrows(String e, String t) {
            if (e.equals("throw")) {
                CompletableFuture<String> f = new CompletableFuture<>();
                f.completeExceptionally(new ShadowException.Named("value", 9));
                return f;
            }
            return CompletableFuture.completedFuture(e + "," + t);
        }
    }

    public static void main(String[] args) throws Exception {
        // Enum variant fields named after the converter's parameters and pattern bindings.
        Shadowing named = new Shadowing.Named("v", 3, true);
        Shadowing back = NameCollisions.roundtripEnum(named);
        assert back instanceof Shadowing.Named : "expected Named variant back";
        Shadowing.Named n = (Shadowing.Named) back;
        assert n.value().equals("v") : "value field";
        assert n.v1() == 3 : "v1 field";
        assert n.v2() : "v2 field";
        assert NameCollisions.enumNamedValue(named).equals("v") : "enumNamedValue";
        assert NameCollisions.roundtripEnum(new Shadowing.Unit()) instanceof Shadowing.Unit : "unit variant";
        Shadowing.Tuple tuple = (Shadowing.Tuple) NameCollisions.roundtripEnum(new Shadowing.Tuple("t", 5L));
        assert tuple.v1().equals("t") && tuple.v2() == 5L : "tuple variant";
        assert NameCollisions.enumNamedValue(new Shadowing.Unit()) == null : "unit has no value";

        // Record and variant fields named after the locals of the generated equals/hashCode.
        // These collisions compile either way, so assert behaviour rather than compilation.
        Shadowed rec = new Shadowed("o", 1, new byte[] { 1, 2 });
        Shadowed same = NameCollisions.roundtripRecord(rec);
        assert rec.equals(rec) : "record equals is not reflexive";
        assert rec.equals(same) : "record equals ignores field values";
        assert !rec.equals(new Shadowed("p", 1, new byte[] { 1, 2 })) : "record equals ignores other";
        assert !rec.equals(new Shadowed("o", 2, new byte[] { 1, 2 })) : "record equals ignores t";
        assert rec.hashCode() == same.hashCode() : "record hashCode unstable";
        assert rec.hashCode() != new Shadowed("o", 1, new byte[] { 3 }).hashCode()
            : "record hashCode ignores result";

        var variant = new ShadowingArrays.Named("o", 1, new byte[] { 1, 2 });
        var variantBack = (ShadowingArrays.Named) NameCollisions.roundtripEnumArrays(variant);
        assert variant.equals(variant) : "variant equals is not reflexive";
        assert variant.equals(variantBack) : "variant equals ignores field values";
        assert !variant.equals(new ShadowingArrays.Named("p", 1, new byte[] { 1, 2 }))
            : "variant equals ignores other";
        assert variant.hashCode() == variantBack.hashCode() : "variant hashCode unstable";
        assert variant.hashCode() != new ShadowingArrays.Named("o", 1, new byte[] { 3 }).hashCode()
            : "variant hashCode ignores result";

        // Error variant fields named after the converter's parameter and binding.
        try {
            NameCollisions.throwNamed("value", 42);
            assert false : "throwNamed should throw";
        } catch (ShadowException.Named e) {
            assert e.value().equals("value") : "error value field";
            assert e.x() == 42 : "error x field";
        }

        // Function and method parameters named after call-path locals.
        assert NameCollisions.syncFn("a", "b", "c", "d", "e").equals("a|b|c|d|e") : "syncFn";
        assert NameCollisions.asyncFn("a", "b", "c", "d", "e", "f", "g", "h").get()
            .equals("a|b|c|d|e|f|g|h") : "asyncFn";
        try (Holder holder = new Holder("tag")) {
            assert holder.syncMethod("h", "o").equals("tag|h|o") : "syncMethod";
            assert holder.asyncMethod("h", "x", "i", "j").get().equals("tag|h|x|i|j") : "asyncMethod";
        }

        // Callback interface parameters named after the implementation class's locals.
        JavaShadow shadow = new JavaShadow();
        assert NameCollisions.callSyncValue(shadow, "p-")
            .equals("p-obj,p-call,p-ret,p-val,p-out,p-low,p-status") : "callSyncValue";
        assert NameCollisions.callSyncPrimitive(shadow, 4, 2) == 42 : "callSyncPrimitive";
        NameCollisions.callSyncVoid(shadow, "n");
        assert shadow.lastVoid.equals("n,status") : "callSyncVoid";
        assert NameCollisions.callSyncThrows(shadow, "ok").equals("ok,status") : "callSyncThrows ok";
        try {
            NameCollisions.callSyncThrows(shadow, "throw");
            assert false : "callSyncThrows should throw";
        } catch (ShadowException.Named e) {
            assert e.x() == 7 : "sync error propagated";
        }

        assert NameCollisions.callAsyncValue(shadow, "q-").get()
            .equals("q-desc,q-success,q-ret,q-result,q-global,q-mh,q-t,q-error,q-status,q-low") : "callAsyncValue";
        assert NameCollisions.callAsyncPrimitive(shadow, 4, 2).get() == 42 : "callAsyncPrimitive";
        NameCollisions.callAsyncVoid(shadow, "n").get();
        assert shadow.lastAsyncVoid.equals("n,ret") : "callAsyncVoid";
        assert NameCollisions.callAsyncThrows(shadow, "ok").get().equals("ok,t") : "callAsyncThrows ok";
        try {
            NameCollisions.callAsyncThrows(shadow, "throw").get();
            assert false : "callAsyncThrows should throw";
        } catch (ExecutionException e) {
            assert e.getCause() instanceof ShadowException.Named : "async error propagated: " + e.getCause();
            assert ((ShadowException.Named) e.getCause()).x() == 9 : "async error field";
        }
    }
}
