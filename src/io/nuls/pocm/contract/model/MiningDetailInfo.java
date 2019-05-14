package io.nuls.pocm.contract.model;

import java.math.BigInteger;

/**
 * detailed information mining
 *
 * @author: Long
 * @date: 2019-03-15
 */
public class MiningDetailInfo {
    /**
     * the mortgage number
     */
    private long depositNumber;

    /**
     * The amount of mining
     */
    private BigInteger miningAmount = BigInteger.ZERO;

    /**
     * Receiving address of Token obtained from mining
     */
    private String receiverMiningAddress;

    /**
     * The number of mining
     */
    private int miningCount;

    /**
     * the award cycle for next mining
     */
    private int nextStartMiningCycle;

    /**
     * Source address of mortgage
     */
    private String depositorAddress;

    public MiningDetailInfo(String miningAddress, String depositorAddress, long depositNumber) {
        this.receiverMiningAddress = miningAddress;
        this.depositorAddress = depositorAddress;
        this.depositNumber = depositNumber;
        this.miningCount = 0;
    }

    public BigInteger getMiningAmount() {
        return miningAmount;
    }

    public void setMiningAmount(BigInteger miningAmount) {
        this.miningAmount = miningAmount;
    }

    public String getReceiverMiningAddress() {
        return receiverMiningAddress;
    }

    public void setReceiverMiningAddress(String receiverMiningAddress) {
        this.receiverMiningAddress = receiverMiningAddress;
    }

    public String getDepositorAddress() {
        return depositorAddress;
    }

    public void setDepositorAddress(String depositorAddress) {
        this.depositorAddress = depositorAddress;
    }

    public int getMiningCount() {
        return miningCount;
    }

    public void setMiningCount(int miningCount) {
        this.miningCount = miningCount;
    }

    public int getNextStartMiningCycle() {
        return nextStartMiningCycle;
    }

    public void setNextStartMiningCycle(int nextStartMiningCycle) {
        this.nextStartMiningCycle = nextStartMiningCycle;
    }

    public long getDepositNumber() {
        return depositNumber;
    }

    public void setDepositNumber(long depositNumber) {
        this.depositNumber = depositNumber;
    }

    @Override
    public String toString() {
        return "{depositNumber:" + depositNumber + ",miningAmount:" + miningAmount.toString() + ",receiverMiningAddress:" + receiverMiningAddress
                + ",miningCount:" + miningCount + ",nextStartMiningCycle:" + nextStartMiningCycle + ",depositorAddress:" + depositorAddress + "}";
    }

}
