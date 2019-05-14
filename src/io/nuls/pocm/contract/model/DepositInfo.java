package io.nuls.pocm.contract.model;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static io.nuls.contract.sdk.Utils.require;

/**
 * Mortgage information
 *
 * @author: Long
 * @date: 2019-03-15
 */
public class DepositInfo {

    /**
     * Mortgagor's address
     */
    private String depositorAddress;

    /**
     * the amount of mortgage
     */
    private BigInteger depositTotalAmount;

    /**
     * Number of mortgages
     */
    private int depositCount;

    /**
     * Mortgage Details List
     */
    private Map<Long, DepositDetailInfo> depositDetailInfos = new HashMap<Long, DepositDetailInfo>();

    public DepositInfo() {
        this.depositTotalAmount = BigInteger.ZERO;
        this.depositCount = 0;
    }

    public DepositInfo(DepositInfo info) {
        this.depositorAddress = info.depositorAddress;
        this.depositTotalAmount = info.depositTotalAmount;
        this.depositCount = info.depositCount;
        this.depositDetailInfos = info.depositDetailInfos;
    }

    public BigInteger getDepositTotalAmount() {
        return depositTotalAmount;
    }

    public void setDepositTotalAmount(BigInteger depositTotalAmount) {
        this.depositTotalAmount = depositTotalAmount;
    }

    public Map<Long, DepositDetailInfo> getDepositDetailInfos() {
        return depositDetailInfos;
    }

    public int getDepositCount() {
        return depositCount;
    }

    public void setDepositCount(int depositCount) {
        this.depositCount = depositCount;
    }


    public String getDepositorAddress() {
        return depositorAddress;
    }

    public void setDepositorAddress(String depositorAddress) {
        this.depositorAddress = depositorAddress;
    }

    /**
     * Find mortgage details based on mortgage number
     *
     * @param depositNumber
     * @return
     */
    public DepositDetailInfo getDepositDetailInfoByNumber(long depositNumber) {
        DepositDetailInfo info = depositDetailInfos.get(depositNumber);
        require(info != null, "Mortgage details of this mortgage number were not found");
        return info;
    }

    /**
     * @param depositNumber
     */
    public void removeDepositDetailInfoByNumber(long depositNumber) {
        depositDetailInfos.remove(depositNumber);
    }

    @Override
    public String toString() {
        return "{depositTotalAmount:" + depositTotalAmount + ",depositorAddress:" + depositorAddress
                + ",depositCount:" + depositCount + ",depositDetailInfos:" + convertMapToString() + "}";
    }

    private String convertMapToString() {
        String detailinfo = "{";
        String temp = "";
        for (Long key : depositDetailInfos.keySet()) {
            DepositDetailInfo detailInfo = depositDetailInfos.get(key);
            temp = detailInfo.toString();
            detailinfo = detailinfo + temp + ",";
        }
        detailinfo = detailinfo.substring(0, detailinfo.length() - 1);
        detailinfo = detailinfo + "}";

        return detailinfo;
    }

}
