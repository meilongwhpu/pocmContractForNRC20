/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2018 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.pocm.contract.util;

import io.nuls.contract.sdk.Address;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 工具类
 * @author: Long
 * @date: 2019-03-15
 */
public class PocmUtil {
    public static BigDecimal toNuls(BigInteger na) {
        return new BigDecimal(na).movePointLeft(8);
    }

    public static BigInteger toNa(BigDecimal nuls) {
        return nuls.scaleByPowerOfTen(8).toBigInteger();
    }

    /**
     * 检查空投数组的数额是否正确
     * @param receiverAmount
     * @return
     */
    public static boolean checkAmount(long[] receiverAmount){
        boolean result=true;
        for(int i=0;i<receiverAmount.length;i++){
            if( receiverAmount[i]< 0){
                result=false;
                break;
            }
        }
        return result;
    }

    /**
     * 计算空投数组的总额
     * @param receiverAmount
     * @return
     */
    public static BigInteger sumAmount(long[] receiverAmount){
        BigInteger amount = BigInteger.ZERO;
        if(receiverAmount.length>0){
            for(int i=0;i<receiverAmount.length;i++){
                amount = amount.add(BigInteger.valueOf((receiverAmount[i])));
         }
        }
        return amount;
    }

    /**
     * 将空投地址数组转换格式
     * @param receiveraddresses
     * @return
     */
    public static Address[] convertStringToAddres(String[] receiveraddresses){
        Address[] addresses = new Address[receiveraddresses.length];
        for(int i=0;i<receiveraddresses.length;i++){
            Address address = new Address(receiveraddresses[i]);
            addresses[i]=address;
        }
        return addresses;
    }

    private static boolean isNumeric(String str){
        for(int i=0;i<str.length();i++){
            int chr=str.charAt(i);
            if(chr<48||chr>57)
                return false;
        }
        return true;
    }

    public static boolean canConvertNumeric(String str){
        String str_trim=str.trim();
        String max=String.valueOf(Integer.MAX_VALUE);
        if(isNumeric(str_trim)){
            if(str_trim.length()<max.length()){
                return true;
            }else if(str_trim.length()==max.length()){
                return str_trim.compareTo(max)<=0;
            }else{
                return false;
            }
        }else{
            return false;
        }
    }

    public static boolean canConvertLongNumeric(String str){
        String str_trim=str.trim();
        String max=String.valueOf(Long.MAX_VALUE);
        if(isNumeric(str_trim)){
            if(str_trim.length()>max.length()){
                return false;
            }else{
                if(str_trim.compareTo(max)>0){
                    return false;
                }else{
                    return true;
                }
            }
        }else{
            return false;
        }
    }
}
