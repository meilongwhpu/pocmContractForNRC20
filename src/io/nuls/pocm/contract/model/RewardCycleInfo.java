package io.nuls.pocm.contract.model;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Reward cycle information
 *
 * @author: Long
 * @date: 2019-04-19
 */
public class RewardCycleInfo {

    /**
     * the reward cycle
     */
    private int rewardingCylce;

    /**
     * The current price reward cycle
     */
    private BigDecimal currentPrice;

    /**
     * Total amount of mortgage
     */
    private BigInteger depositAmount;

    /**
     * Number of reward cycles that differ from the last statistics
     */
    private int differCycleValue;

    public BigInteger getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigInteger depositAmount) {
        this.depositAmount = depositAmount;
    }

    public int getRewardingCylce() {
        return rewardingCylce;
    }

    public void setRewardingCylce(int rewardingCylce) {
        this.rewardingCylce = rewardingCylce;
    }

    public int getDifferCycleValue() {
        return differCycleValue;
    }

    public void setDifferCycleValue(int differCycleValue) {
        this.differCycleValue = differCycleValue;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    @Override
    public String toString() {
        return "{rewardingCylce:" + rewardingCylce + ",currentPrice:" + currentPrice.toString() + ",depositAmount:" + depositAmount
                + ",differCycleValue:" + differCycleValue + "}";
    }
}
