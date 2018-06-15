/*
 * Copyright 2007 ZXing authors Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

package xju.dctcamera.utils.dct.com.google.zxing.common.reedsolomon;

/**
 * 谷歌提供的纠删码算法
 */


/**
 * <p>
 * Implements Reed-Solomon decoding, as the name implies.
 * </p>
 * <p>
 * The algorithm will not be explained here, but the following references were helpful in creating this implementation:
 * </p>
 * <ul>
 * <li>Bruce Maggs. <a href="http://www.cs.cmu.edu/afs/cs.cmu.edu/project/pscico-guyb/realworld/www/rs_decode.ps">
 * "Decoding Reed-Solomon Codes"</a> (see discussion of Forney's Formula)</li>
 * <li>J.I. Hall. <a href="www.mth.msu.edu/~jhall/classes/codenotes/GRS.pdf">
 * "Chapter 5. Generalized Reed-Solomon Codes"</a> (see discussion of Euclidean algorithm)</li>
 * </ul>
 * <p>
 * Much credit is due to William Rucklidge since portions of this code are an indirect port of his C++ Reed-Solomon
 * implementation.
 * </p>
 * 
 * @author Sean Owen
 * @author William Rucklidge
 * @author sanfordsquires
 */
public final class ReedSolomonDecoder {

    private final GenericGF field;

    public ReedSolomonDecoder(final GenericGF field) {
        this.field = field;
    }

    /**
     * <p>
     * Decodes given set of received codewords, which include both data and error-correction codewords. Really, this
     * means it uses Reed-Solomon to detect and correct errors, in-place, in the input.
     * </p>
     * 
     * @param received data and error-correction codewords
     * @param twoS number of error-correction codewords available
     * @throws ReedSolomonException if decoding fails for any reason
     */
    public void decode(final int[] received, final int twoS) throws ReedSolomonException {
        final GenericGFPoly poly = new GenericGFPoly(this.field, received);
        final int[] syndromeCoefficients = new int[twoS];
        final boolean dataMatrix = this.field.equals(GenericGF.DATA_MATRIX_FIELD_256);
        boolean noError = true;
        for (int i = 0; i < twoS; i++) {
            // Thanks to sanfordsquires for this fix:
            final int eval = poly.evaluateAt(this.field.exp(dataMatrix ? i + 1 : i));
            syndromeCoefficients[syndromeCoefficients.length - 1 - i] = eval;
            if (eval != 0) {
                noError = false;
            }
        }
        if (noError)
            return;
        final GenericGFPoly syndrome = new GenericGFPoly(this.field, syndromeCoefficients);
        final GenericGFPoly[] sigmaOmega = runEuclideanAlgorithm(this.field.buildMonomial(twoS, 1), syndrome, twoS);
        final GenericGFPoly sigma = sigmaOmega[0];
        final GenericGFPoly omega = sigmaOmega[1];
        final int[] errorLocations = findErrorLocations(sigma);
        final int[] errorMagnitudes = findErrorMagnitudes(omega, errorLocations, dataMatrix);
        for (int i = 0; i < errorLocations.length; i++) {
            final int position = received.length - 1 - this.field.log(errorLocations[i]);
            if (position < 0)
                throw new ReedSolomonException("Bad error location");
            received[position] = GenericGF.addOrSubtract(received[position], errorMagnitudes[i]);
        }
    }

    private int[] findErrorLocations(final GenericGFPoly errorLocator) throws ReedSolomonException {
        // This is a direct application of Chien's search
        final int numErrors = errorLocator.getDegree();
        if (numErrors == 1)
            return new int[] {
                errorLocator.getCoefficient(1) };
        final int[] result = new int[numErrors];
        int e = 0;
        for (int i = 1; i < this.field.getSize() && e < numErrors; i++) {
            if (errorLocator.evaluateAt(i) == 0) {
                result[e] = this.field.inverse(i);
                e++;
            }
        }
        if (e != numErrors)
            throw new ReedSolomonException("Error locator degree does not match number of roots");
        return result;
    }

    private int[] findErrorMagnitudes(final GenericGFPoly errorEvaluator, final int[] errorLocations,
            final boolean dataMatrix) {
        // This is directly applying Forney's Formula
        final int s = errorLocations.length;
        final int[] result = new int[s];
        for (int i = 0; i < s; i++) {
            final int xiInverse = this.field.inverse(errorLocations[i]);
            int denominator = 1;
            for (int j = 0; j < s; j++) {
                if (i != j) {
                    // denominator = field.multiply(denominator,
                    // GenericGF.addOrSubtract(1, field.multiply(errorLocations[j], xiInverse)));
                    // Above should work but fails on some Apple and Linux JDKs due to a Hotspot bug.
                    // Below is a funny-looking workaround from Steven Parkes
                    final int term = this.field.multiply(errorLocations[j], xiInverse);
                    final int termPlus1 = (term & 0x1) == 0 ? term | 1 : term & ~1;
                    denominator = this.field.multiply(denominator, termPlus1);
                }
            }
            result[i] = this.field.multiply(errorEvaluator.evaluateAt(xiInverse), this.field.inverse(denominator));
            // Thanks to sanfordsquires for this fix:
            if (dataMatrix) {
                result[i] = this.field.multiply(result[i], xiInverse);
            }
        }
        return result;
    }

    private GenericGFPoly[] runEuclideanAlgorithm(GenericGFPoly a, GenericGFPoly b, final int R)
            throws ReedSolomonException {
        // Assume a's degree is >= b's
        if (a.getDegree() < b.getDegree()) {
            final GenericGFPoly temp = a;
            a = b;
            b = temp;
        }

        GenericGFPoly rLast = a;
        GenericGFPoly r = b;
        GenericGFPoly sLast = this.field.getOne();
        GenericGFPoly s = this.field.getZero();
        GenericGFPoly tLast = this.field.getZero();
        GenericGFPoly t = this.field.getOne();

        // Run Euclidean algorithm until r's degree is less than R/2
        while (r.getDegree() >= R / 2) {
            final GenericGFPoly rLastLast = rLast;
            final GenericGFPoly sLastLast = sLast;
            final GenericGFPoly tLastLast = tLast;
            rLast = r;
            sLast = s;
            tLast = t;

            // Divide rLastLast by rLast, with quotient in q and remainder in r
            if (rLast.isZero())
                // Oops, Euclidean algorithm already terminated?
                throw new ReedSolomonException("r_{i-1} was zero");
            r = rLastLast;
            GenericGFPoly q = this.field.getZero();
            final int denominatorLeadingTerm = rLast.getCoefficient(rLast.getDegree());
            final int dltInverse = this.field.inverse(denominatorLeadingTerm);
            while (r.getDegree() >= rLast.getDegree() && !r.isZero()) {
                final int degreeDiff = r.getDegree() - rLast.getDegree();
                final int scale = this.field.multiply(r.getCoefficient(r.getDegree()), dltInverse);
                q = q.addOrSubtract(this.field.buildMonomial(degreeDiff, scale));
                r = r.addOrSubtract(rLast.multiplyByMonomial(degreeDiff, scale));
            }

            s = q.multiply(sLast).addOrSubtract(sLastLast);
            t = q.multiply(tLast).addOrSubtract(tLastLast);
        }

        final int sigmaTildeAtZero = t.getCoefficient(0);
        if (sigmaTildeAtZero == 0)
            throw new ReedSolomonException("sigmaTilde(0) was zero");

        final int inverse = this.field.inverse(sigmaTildeAtZero);
        final GenericGFPoly sigma = t.multiply(inverse);
        final GenericGFPoly omega = r.multiply(inverse);
        return new GenericGFPoly[] {
                sigma, omega };
    }

}
