/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.phpsrc;


/**
 * Compilation options for the PHP backend.
 *
 */
public final class SoyPhpSrcOptions implements Cloneable {
    /** The full module and fn path to a runtime library for determining global directionality. */
    private final String bidiIsRtlFn;

    /** The full module and class path to a runtime library for translation. */
    private final String translationClass;

    public SoyPhpSrcOptions(String bidiIsRtlFn, String translationClass) {
        this.bidiIsRtlFn = bidiIsRtlFn;
        this.translationClass = translationClass;
    }

    private SoyPhpSrcOptions(SoyPhpSrcOptions orig) {
        this.bidiIsRtlFn = orig.bidiIsRtlFn;
        this.translationClass = orig.translationClass;
    }

    public String getBidiIsRtlFn() {
        return bidiIsRtlFn;
    }

    public String getTranslationClass() {
        return translationClass;
    }

    @Override public final SoyPhpSrcOptions clone() {
        return new SoyPhpSrcOptions(this);
    }

}
