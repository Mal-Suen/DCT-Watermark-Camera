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
 * Represents a polynomial whose coefficients are elements of a GF. Instances of this class are immutable.
 * </p>
 * <p>
 * Much credit is due to William Rucklidge since portions of this code are an indirect port of his C++ Reed-Solomon
 * implementation.
 * </p>
 * 
 * @author Sean Owen
 */
final class GenericGFPoly {

    private final GenericGF field;

    private final int[] coefficients;

    /**
     * @param field the {@link GenericGF} instance representing the field to use to perform computations
     * @param coefficients coefficients as ints representing elements of GF(size), arranged from most significant
     *            (highest-power term) coefficient to least significant
     * @throws IllegalArgumentException if argument is null or empty, or if leading coefficient is 0 and this is not a
     *             constant polynomial (that is, it is not the monomial "0")
     */
    GenericGFPoly(final GenericGF field, final int[] coefficients) {
        if (coefficients == null || coefficients.length == 0)
            throw new IllegalArgumentException();
        this.field = field;
        final int coefficientsLength = coefficients.length;
        if (coefficientsLength > 1 && coefficients[0] == 0) {
            // Leading term must be non-zero for anything except the constant polynomial "0"
            int firstNonZero = 1;
            while (firstNonZero < coefficientsLength && coefficients[firstNonZero] == 0) {
                firstNonZero++;
            }
            if (firstNonZero == coefficientsLength) {
                this.coefficients = field.getZero().coefficients;
            } else {
                this.coefficients = new int[coefficientsLength - firstNonZero];
                System.arraycopy(coefficients, firstNonZero, this.coefficients, 0, this.coefficients.length);
            }
        } else {
            this.coefficients = coefficients;
        }
    }

    GenericGFPoly addOrSubtract(final GenericGFPoly other) {
        if (!this.field.equals(other.field))
            throw new IllegalArgumentException("GenericGFPolys do not have same GenericGF field");
        if (isZero())
            return other;
        if (other.isZero())
            return this;

        int[] smallerCoefficients = this.coefficients;
        int[] largerCoefficients = other.coefficients;
        if (smallerCoefficients.length > largerCoefficients.length) {
            final int[] temp = smallerCoefficients;
            smallerCoefficients = largerCoefficients;
            largerCoefficients = temp;
        }
        final int[] sumDiff = new int[largerCoefficients.length];
        final int lengthDiff = largerCoefficients.length - smallerCoefficients.length;
        // Copy high-order terms only found in higher-degree polynomial's coefficients
        System.arraycopy(largerCoefficients, 0, sumDiff, 0, lengthDiff);

        for (int i = lengthDiff; i < largerCoefficients.length; i++) {
            sumDiff[i] = GenericGF.addOrSubtract(smallerCoefficients[i - lengthDiff], largerCoefficients[i]);
        }

        return new GenericGFPoly(this.field, sumDiff);
    }

    GenericGFPoly[] divide(final GenericGFPoly other) {
        if (!this.field.equals(other.field))
            throw new IllegalArgumentException("GenericGFPolys do not have same GenericGF field");
        if (other.isZero())
            throw new IllegalArgumentException("Divide by 0");

        GenericGFPoly quotient = this.field.getZero();
        GenericGFPoly remainder = this;

        final int denominatorLeadingTerm = other.getCoefficient(other.getDegree());
        final int inverseDenominatorLeadingTerm = this.field.inverse(denominatorLeadingTerm);

        while (remainder.getDegree() >= other.getDegree() && !remainder.isZero()) {
            final int degreeDifference = remainder.getDegree() - other.getDegree();
            final int scale = this.field.multiply(remainder.getCoefficient(remainder.getDegree()),
                    inverseDenominatorLeadingTerm);
            final GenericGFPoly term = other.multiplyByMonomial(degreeDifference, scale);
            final GenericGFPoly iterationQuotient = this.field.buildMonomial(degreeDifference, scale);
            quotient = quotient.addOrSubtract(iterationQuotient);
            remainder = remainder.addOrSubtract(term);
        }

        return new GenericGFPoly[] {
                quotient, remainder };
    }

    /**
     * @return evaluation of this polynomial at a given point
     */
    int evaluateAt(final int a) {
        if (a == 0)
            // Just return the x^0 coefficient
            return getCoefficient(0);
        final int size = this.coefficients.length;
        if (a == 1) {
            // Just the sum of the coefficients
            int result = 0;
            for (int i = 0; i < size; i++) {
                result = GenericGF.addOrSubtract(result, this.coefficients[i]);
            }
            return result;
        }
        int result = this.coefficients[0];
        for (int i = 1; i < size; i++) {
            result = GenericGF.addOrSubtract(this.field.multiply(a, result), this.coefficients[i]);
        }
        return result;
    }

    /**
     * @return coefficient of x^degree term in this polynomial
     */
    int getCoefficient(final int degree) {
        return this.coefficients[this.coefficients.length - 1 - degree];
    }

    int[] getCoefficients() {
        return this.coefficients;
    }

    /**
     * @return degree of this polynomial
     */
    int getDegree() {
        return this.coefficients.length - 1;
    }

    /**
     * @return true iff this polynomial is the monomial "0"
     */
    boolean isZero() {
        return this.coefficients[0] == 0;
    }

    GenericGFPoly multiply(final GenericGFPoly other) {
        if (!this.field.equals(other.field))
            throw new IllegalArgumentException("GenericGFPolys do not have same GenericGF field");
        if (isZero() || other.isZero())
            return this.field.getZero();
        final int[] aCoefficients = this.coefficients;
        final int aLength = aCoefficients.length;
        final int[] bCoefficients = other.coefficients;
        final int bLength = bCoefficients.length;
        final int[] product = new int[aLength + bLength - 1];
        for (int i = 0; i < aLength; i++) {
            final int aCoeff = aCoefficients[i];
            for (int j = 0; j < bLength; j++) {
                product[i + j] = GenericGF.addOrSubtract(product[i + j], this.field.multiply(aCoeff, bCoefficients[j]));
            }
        }
        return new GenericGFPoly(this.field, product);
    }

    GenericGFPoly multiply(final int scalar) {
        if (scalar == 0)
            return this.field.getZero();
        if (scalar == 1)
            return this;
        final int size = this.coefficients.length;
        final int[] product = new int[size];
        for (int i = 0; i < size; i++) {
            product[i] = this.field.multiply(this.coefficients[i], scalar);
        }
        return new GenericGFPoly(this.field, product);
    }

    GenericGFPoly multiplyByMonomial(final int degree, final int coefficient) {
        if (degree < 0)
            throw new IllegalArgumentException();
        if (coefficient == 0)
            return this.field.getZero();
        final int size = this.coefficients.length;
        final int[] product = new int[size + degree];
        for (int i = 0; i < size; i++) {
            product[i] = this.field.multiply(this.coefficients[i], coefficient);
        }
        return new GenericGFPoly(this.field, product);
    }

    @Override
    public String toString() {
        final StringBuffer result = new StringBuffer(8 * getDegree());
        for (int degree = getDegree(); degree >= 0; degree--) {
            int coefficient = getCoefficient(degree);
            if (coefficient != 0) {
                if (coefficient < 0) {
                    result.append(" - ");
                    coefficient = -coefficient;
                } else {
                    if (result.length() > 0) {
                        result.append(" + ");
                    }
                }
                if (degree == 0 || coefficient != 1) {
                    final int alphaPower = this.field.log(coefficient);
                    if (alphaPower == 0) {
                        result.append('1');
                    } else if (alphaPower == 1) {
                        result.append('a');
                    } else {
                        result.append("a^");
                        result.append(alphaPower);
                    }
                }
                if (degree != 0) {
                    if (degree == 1) {
                        result.append('x');
                    } else {
                        result.append("x^");
                        result.append(degree);
                    }
                }
            }
        }
        return result.toString();
    }

}
