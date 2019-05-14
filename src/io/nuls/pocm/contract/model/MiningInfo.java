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
package io.nuls.pocm.contract.model;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static io.nuls.contract.sdk.Utils.require;

/**
 * Mining information
 * @author: Long
 * @date: 2019-03-15
 */
public class MiningInfo {

    /**
     * Total mining amount
     */
    private BigInteger totalMining;

    /**
     * Mining amount received
     */
    private BigInteger receivedMining;

    /**
     * Details of mining
     */
    private Map<Long,MiningDetailInfo> miningDetailInfos =new HashMap<Long,MiningDetailInfo>();

    public MiningInfo() {
        this.totalMining = BigInteger.ZERO;
        this.receivedMining=BigInteger.ZERO;
    }

    public MiningInfo(MiningInfo info){
        this.totalMining=info.totalMining;
        this.receivedMining=info.receivedMining;
        this.miningDetailInfos=info.miningDetailInfos;
    }


    public BigInteger getTotalMining() {
        return totalMining;
    }

    public void setTotalMining(BigInteger totalMining) {
        this.totalMining = totalMining;
    }

    public BigInteger getReceivedMining() {
        return receivedMining;
    }

    public void setReceivedMining(BigInteger receivedMining) {
        this.receivedMining = receivedMining;
    }

    public Map<Long, MiningDetailInfo> getMiningDetailInfos() {
        return miningDetailInfos;
    }

    public void setMiningDetailInfos(Map<Long, MiningDetailInfo> miningDetailInfos) {
        this.miningDetailInfos = miningDetailInfos;
    }
    /**
     * Find mining details based on mortgage number
     * @param depositNumber
     * @return
     */
    public MiningDetailInfo getMiningDetailInfoByNumber(long depositNumber){
        MiningDetailInfo info=miningDetailInfos.get(depositNumber);
        require(info != null, "Mining details of this mortgage number were not found");
        return info;
    }

    public void removeMiningDetailInfoByNumber(long depositNumber){
        miningDetailInfos.remove(depositNumber);
    }

    @Override
    public String toString(){
        return "{totalMining:"+totalMining.toString()+",receivedMining:"+receivedMining.toString()
                +",miningDetailInfo:"+this.convertMapToString()+"}";
    }

    private  String convertMapToString(){
        String detailinfo ="{";
        String temp="";
        for (Long key : miningDetailInfos.keySet()) {
            MiningDetailInfo detailInfo=  miningDetailInfos.get(key);
            temp =detailInfo.toString();
            detailinfo=detailinfo+temp+",";
        }
        detailinfo=detailinfo.substring(0,detailinfo.length()-1)+"}";
        return detailinfo;
    }

}
