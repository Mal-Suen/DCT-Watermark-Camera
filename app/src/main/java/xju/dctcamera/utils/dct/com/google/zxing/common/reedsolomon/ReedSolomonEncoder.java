/*
 * Copyright 2008 ZXing authors Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

package xju.dctcamera.utils.dct.com.google.zxing.common.reedsolomon;

import java.util.Vector;



/**
 * 谷歌提供的纠删码算法
 */

/**
 * <p>
 * Implements Reed-Solomon encoding, as the name implies.
 * </p>
 * 
 * @author Sean Owen
 * @author William Rucklidge
 */
@SuppressWarnings({
        "rawtypes", "unchecked" })
public final class ReedSolomonEncoder {

    private final GenericGF field;

    private final Vector cachedGenerators;

    public ReedSolomonEncoder(final GenericGF field) {
        if (!GenericGF.QR_CODE_FIELD_256.equals(field))
            throw new IllegalArgumentException("Only QR Code is supported at this time");
        this.field = field;
        this.cachedGenerators = new Vector();
        this.cachedGenerators.addElement(new GenericGFPoly(field, new int[] {
            1 }));
    }

    private GenericGFPoly buildGenerator(final int degree) {
        if (degree >= this.cachedGenerators.size()) {
            GenericGFPoly lastGenerator = (GenericGFPoly) this.cachedGenerators
                    .elementAt(this.cachedGenerators.size() - 1);
            for (int d = this.cachedGenerators.size(); d <= degree; d++) {
                final GenericGFPoly nextGenerator = lastGenerator.multiply(new GenericGFPoly(this.field, new int[] {
                        1, this.field.exp(d - 1) }));
                this.cachedGenerators.addElement(nextGenerator);
                lastGenerator = nextGenerator;
            }
        }
        return (GenericGFPoly) this.cachedGenerators.elementAt(degree);
    }

    public void encode(final int[] toEncode, final int ecBytes) {
        if (ecBytes == 0)
            throw new IllegalArgumentException("No error correction bytes");
        final int dataBytes = toEncode.length - ecBytes;
        if (dataBytes <= 0)
            throw new IllegalArgumentException("No data bytes provided");
        final GenericGFPoly generator = buildGenerator(ecBytes);
        final int[] infoCoefficients = new int[dataBytes];
        System.arraycopy(toEncode, 0, infoCoefficients, 0, dataBytes);
        GenericGFPoly info = new GenericGFPoly(this.field, infoCoefficients);
        info = info.multiplyByMonomial(ecBytes, 1);
        final GenericGFPoly remainder = info.divide(generator)[1];
        final int[] coefficients = remainder.getCoefficients();
        final int numZeroCoefficients = ecBytes - coefficients.length;
        for (int i = 0; i < numZeroCoefficients; i++) {
            toEncode[dataBytes + i] = 0;
        }
        System.arraycopy(coefficients, 0, toEncode, dataBytes + numZeroCoefficients, coefficients.length);
    }

}
