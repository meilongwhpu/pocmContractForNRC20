package io.nuls.pocm.contract.util;

import io.nuls.contract.sdk.Address;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Tool
 *
 * @author: Long
 * @date: 2019-03-15
 */
public class PocmUtil {

    public static final BigDecimal HLAVING = new BigDecimal("2");

    public static BigDecimal toNuls(BigInteger na) {
        return new BigDecimal(na).movePointLeft(8);
    }

    public static BigInteger toNa(BigDecimal nuls) {
        return nuls.scaleByPowerOfTen(8).toBigInteger();
    }

    /**
     * The precision of price should not exceed the precision of definition.
     *
     * @param price    Price
     * @param decimals precision
     * @return
     */
    public static boolean checkMaximumDecimals(BigDecimal price, int decimals) {
        BigInteger a = price.movePointRight(decimals).toBigInteger().multiply(BigInteger.TEN);
        BigInteger b = price.movePointRight(decimals + 1).toBigInteger();
        if (a.compareTo(b) != 0) {
            return false;
        }
        return true;
    }

    /**
     * Check that the amount of the airdrop array
     *
     * @param receiverAmount
     * @return
     */
    public static boolean checkAmount(long[] receiverAmount) {
        boolean result = true;
        for (int i = 0; i < receiverAmount.length; i++) {
            if (receiverAmount[i] < 0) {
                result = false;
                break;
            }
        }
        return result;
    }

    /**
     * Calculate the total number of airdrop arrays
     *
     * @param receiverAmount
     * @return
     */
    public static BigInteger sumAmount(long[] receiverAmount) {
        BigInteger amount = BigInteger.ZERO;
        if (receiverAmount.length > 0) {
            for (int i = 0; i < receiverAmount.length; i++) {
                amount = amount.add(BigInteger.valueOf((receiverAmount[i])));
            }
        }
        return amount;
    }

    /**
     * Converting Airdrop Address Array to Format
     *
     * @param receiveraddresses
     * @return
     */
    public static Address[] convertStringToAddres(String[] receiveraddresses) {
        Address[] addresses = new Address[receiveraddresses.length];
        for (int i = 0; i < receiveraddresses.length; i++) {
            Address address = new Address(receiveraddresses[i]);
            addresses[i] = address;
        }
        return addresses;
    }

    private static boolean isNumeric(String str) {
        for (int i = 0; i < str.length(); i++) {
            int chr = str.charAt(i);
            if (chr < 48 || chr > 57) {
                return false;
            }

        }
        return true;
    }

    public static boolean canConvertNumeric(String str, String maxValue) {
        String trimStr = str.trim();
        if (isNumeric(trimStr)) {
            if (trimStr.length() < maxValue.length()) {
                return true;
            } else if (trimStr.length() == maxValue.length()) {
                return trimStr.compareTo(maxValue) <= 0;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean checkValidity(String str) {
        if (str == null) {
            return false;
        }
        String strTmp = str.trim();
        if (strTmp.length() > 0 && strTmp.length() < 21) {
            if (strTmp.endsWith("_") || strTmp.startsWith("_")) {
                return false;
            }
            for (int i = 0; i < strTmp.length(); i++) {
                int chr = strTmp.charAt(i);
                if (chr < 48 || (chr > 57 && chr < 65) || (chr > 90 && chr < 95) || (chr > 95 && chr < 97) || chr > 122) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

}
