package io.nuls.pocm.contract.model;

import java.math.BigInteger;

/**
 * Mortgage detailed information
 *
 * @author: Long
 * @date: 2019-03-15
 */
public class DepositDetailInfo {

    /**
     * Mortgage number
     */
    private long depositNumber;
    /**
     * Mortgage amount (unit: na)
     */
    private BigInteger depositAmount = BigInteger.ZERO;

    /**
     * Mortgage start height
     */
    private long depositHeight;

    /**
     * Get Token's Receive Address
     */
    private String miningAddress;

    public BigInteger getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigInteger depositAmount) {
        this.depositAmount = depositAmount;
    }

    public long getDepositHeight() {
        return depositHeight;
    }

    public void setDepositHeight(long depositHeight) {
        this.depositHeight = depositHeight;
    }

    public String getMiningAddress() {
        return miningAddress;
    }

    public void setMiningAddress(String miningAddress) {
        this.miningAddress = miningAddress;
    }

    public long getDepositNumber() {
        return depositNumber;
    }

    public void setDepositNumber(long depositNumber) {
        this.depositNumber = depositNumber;
    }

    @Override
    public String toString() {
        return "{depositNumber:" + depositNumber + ",depositHeight:" + depositHeight
                + ",miningAddress:" + miningAddress + ",depositAmount:" + depositAmount + "}";
    }
}
