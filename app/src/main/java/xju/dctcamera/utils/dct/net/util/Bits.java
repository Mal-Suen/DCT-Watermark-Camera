/*
 * Copyright 2012 by Christoph Gaffga licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

package xju.dctcamera.utils.dct.net.util;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import xju.dctcamera.utils.dct.com.google.zxing.common.reedsolomon.GenericGF;
import xju.dctcamera.utils.dct.com.google.zxing.common.reedsolomon.ReedSolomonDecoder;
import xju.dctcamera.utils.dct.com.google.zxing.common.reedsolomon.ReedSolomonEncoder;
import xju.dctcamera.utils.dct.com.google.zxing.common.reedsolomon.ReedSolomonException;

/**
 * bits工具
 * 
 *
 */
public class Bits {

    /**
     * 解压bits
     * @param bits
     * @return
     * @throws IOException
     */
    public static Bits bitsGZIPDecode(final Bits bits) throws IOException {
        final ByteArrayInputStream byteIn = new ByteArrayInputStream(bits.getData());
        final GZIPInputStream zipIn = new GZIPInputStream(byteIn);
        int b;
        final Bits result = new Bits();
        while ((b = zipIn.read()) >= 0) {
            result.addValue(b, 8);
        }
        return result;
    }

    /**
     * 压缩bits
     * @param bits
     * @return
     */
    public static Bits bitsGZIPEncode(final Bits bits) {
        try {
            final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            final GZIPOutputStream zipOut = new GZIPOutputStream(byteOut);
            zipOut.write(bits.getData());
            zipOut.close();
            final Bits result = new Bits();
            result.addData(byteOut.toByteArray());
            return result;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 使用谷歌的纠删码进行解码
     * @param bits
     * @param n
     * @return
     * @throws ReedSolomonException
     */
    public static Bits bitsReedSolomonDecode(final Bits bits, final int n) throws ReedSolomonException {
        int[] data = new Bits(bits.getBits(0, bits.size() - n * 8)).getBytes();
        data = Arrays.copyOf(data, data.length + n);
        for (int i = 0; i < n; i++) {
            data[data.length - n + i] = (int) bits.getValue(bits.size() - n * 8 + i * 8, 8);
        }
        final ReedSolomonDecoder dec = new ReedSolomonDecoder(GenericGF.QR_CODE_FIELD_256);
        dec.decode(data, n);
        final Bits result = new Bits();
        result.addBytes(Arrays.copyOf(data, data.length - n));
        return result;
    }

    /**
     * 使用谷歌的纠删码进行编码
     * @param bits
     * @param n
     * @return
     */
    public static Bits bitsReedSolomonEncode(final Bits bits, final int n) {
        final int[] data = Arrays.copyOf(bits.getBytes(), (bits.size() + 7) / 8 + n);
        final ReedSolomonEncoder enc = new ReedSolomonEncoder(GenericGF.QR_CODE_FIELD_256);
        enc.encode(data, n);
        final Bits result = new Bits(bits);
        for (int i = data.length - n; i < data.length; i++) {
            result.addValue(data[i], 8);
        }
        return result;
    }

    /**
     * 测试方法
     * @param args
     */
    public static void main(final String[] args) {

        final Bits bits = new Bits();
        bits.addBit(true);
        bits.addBit(false);
        bits.addBit(true);
        System.out.println(bits);
        System.out.println(bits.getValue(0, 3));
        bits.addValue(6, 3);
        System.out.println(bits);
        System.out.println(bits.getValue(3, 3));
        byte[] data = bits.getData();
        for (final byte element : data) {
            System.out.print(element + " ");
        }
        System.out.println();
        System.out.println("------");
        bits.reset();
        bits.addData(data);
        System.out.println(bits);
        data[0] = (byte) 0xFF;
        bits.addData(data);
        System.out.println(bits);
        data = bits.getData();
        for (final byte element : data) {
            System.out.print(element + " ");
        }
        System.out.println();
        System.out.println("------");
        final int[] bytes = bits.getBytes();
        for (final int b : bytes) {
            System.out.print(b + " ");
        }
        System.out.println();
        System.out.println("------");

        System.out.println(bitsGZIPEncode(bits));
        try {
            System.out.println(bitsGZIPDecode(bitsGZIPEncode(bits)));
        } catch (final IOException e) {
            e.printStackTrace();
        }
        System.out.println("------");

        final Bits bitsRS = bitsReedSolomonEncode(bits, 2);
        System.out.println(bitsRS);
        try {
            System.out.println(bitsReedSolomonDecode(bitsRS, 2));
            bitsRS.setBit(10, false);
            System.out.println(bitsRS);
            System.out.println(bitsReedSolomonDecode(bitsRS, 2));
        } catch (final ReedSolomonException e) {
            e.printStackTrace();
        }

    }

    /**
     * 内部bit链表
     */
    private final List<Boolean> bits;

    /**
     * 读取计数器
     */
    private int readPosition = 0;

    public Bits() {
        this.bits = new ArrayList<Boolean>();
    }

    public Bits(final Bits bits) {
        this.bits = new ArrayList<Boolean>(bits.bits);
    }

    public Bits(final Collection<Boolean> bits) {
        this.bits = new ArrayList<Boolean>(bits);
    }

    public void addBit(final boolean bit) {
        this.bits.add(bit);
    }

    public void addBits(final boolean[] bits) {
        for (final boolean bit : bits) {
            addBit(bit);
        }
    }

    public void addBits(final Collection<Boolean> bits) {
        for (final Boolean bit : bits) {
            addBit(bit);
        }
    }

    public void addBytes(final int[] bytes) {
        addBytes(bytes, bytes.length);
    }

    public void addBytes(final int[] bytes, final int len) {
        for (int i = 0; i < len; i++) {
            int bit = 0x01;
            for (int j = 0; j < 8; j++) {
                addBit((bytes[i] & bit) > 0);
                bit <<= 1;
            }
        }
    }

    public void addData(final byte[] data) {
        addData(data, data.length);
    }

    public void addData(final byte[] data, final int len) {
        for (int i = 0; i < len; i++) {
            int bit = 0x01;
            for (int j = 0; j < 8; j++) {
                addBit((data[i] & bit) > 0);
                bit <<= 1;
            }
        }
    }

    public void addValue(final long bits, final int len) {
        long bit = 0x01;
        for (int i = 0; i < len; i++) {
            addBit((bits & bit) > 0);
            bit <<= 1;
        }
    }

    public boolean getBit(final int index) {
        return this.bits.get(index);
    }

    public List<Boolean> getBits() {
        return this.bits;
    }

    /**
     * 返回bits链表的子表
     * @param fromIndex
     * @param toIndex
     * @return
     */
    public List<Boolean> getBits(final int fromIndex, final int toIndex) {
        return this.bits.subList(fromIndex, toIndex);
    }

    public int[] getBytes() {
        final int[] bytes = new int[(this.bits.size() + 7) / 8];
        for (int i = 0; i < bytes.length; i++) {
            int bit = 0x01;
            for (int j = 0; j < 8 && i * 8 + j < this.bits.size(); j++) {
                if (this.bits.get(i * 8 + j)) {
                    bytes[i] |= bit;
                }
                bit <<= 1;
            }
        }
        return bytes;
    }

    public byte[] getData() {
        final byte[] data = new byte[(this.bits.size() + 7) / 8];
        for (int i = 0; i < data.length; i++) {
            int bit = 0x01;
            for (int j = 0; j < 8 && i * 8 + j < this.bits.size(); j++) {
                if (this.bits.get(i * 8 + j)) {
                    data[i] |= bit;
                }
                bit <<= 1;
            }
        }
        return data;
    }

    public long getValue(final int index, final int len) {
        long result = 0;
        long bit = 0x01;
        for (int i = index; i < index + len; i++) {
            if (this.bits.get(i)) {
                result |= bit;
            }
            bit <<= 1;
        }
        return result;
    }

    public boolean hasNext() {
        return this.readPosition < this.bits.size();
    }

    public boolean hasNext(final int len) {
        return this.readPosition + len < this.bits.size() + 1;
    }

    public boolean popBit() {
        return getBit(this.readPosition++);
    }

    public List<Boolean> popBits(final int len) {
        this.readPosition += len;
        return getBits(this.readPosition - len, this.readPosition);
    }

    public long popValue(final int len) {
        this.readPosition += len;
        return getValue(this.readPosition - len, len);
    }

    public void reset() {
        this.bits.clear();
        this.readPosition = 0;
    }

    public void setBit(final int index, final boolean bit) {
        this.bits.set(index, bit);
    }

    public int size() {
        return this.bits.size();
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(this.bits.size());
        for (int i = 0; i < this.bits.size(); i++) {
            buf.append(this.bits.get(i) ? '1' : '0');
        }
        return buf.toString();
    }

}
