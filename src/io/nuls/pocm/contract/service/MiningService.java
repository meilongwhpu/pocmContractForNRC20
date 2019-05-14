package io.nuls.pocm.contract.service;

import io.nuls.contract.sdk.Block;
import io.nuls.pocm.contract.model.*;
import io.nuls.pocm.contract.util.PocmUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static io.nuls.contract.sdk.Utils.require;
import static io.nuls.pocm.contract.util.PocmUtil.toNuls;

/**
 * @author: Long
 * @date: 2019-03-15
 */
public class MiningService {

    /**
     * The initial price of NULS, each award cycle all the number of split XX token mortgage
     */
    private BigDecimal initialPrice;

    /**
     * Contract creation height
     */
    private long createHeight;

    /**
     * Reward distribution cycle (parameter type is digital, every XX block is issued once)
     */
    private int awardingCycle;

    /**
     * Award halving period (optional parameters, if selected, the parameter type is digital, reward halving per XX height)
     */
    private int rewardHalvingCycle;

    /**
     * precision
     */
    private int decimals;

    /**
     * User mining information (key is the Token address for receiving mining)
     */
    private Map<String, MiningInfo> mingUsers = new HashMap<String, MiningInfo>();

    /**
     * Index of mortgage amount for each incentive cycle. Key: Reward Cycle, V: Corresponding Number in Reward Cycle Information Queue
     */
    private Map<Integer, Integer> totalDepositIndex = new LinkedHashMap<Integer, Integer>();

    /**
     * Reward cycle information list, and the combined use of index table
     */
    private List<RewardCycleInfo> totalDepositList = new LinkedList<RewardCycleInfo>();

    /**
     * The next half the height of reward
     */
    private long nextRewardHalvingHeight;

    /**
     * The last calculation of the reward cycle
     */
    private int lastCalcCycle = 0;

    /**
     * The current price, the current NULS award cycle all mortgage number XX token share
     */
    private BigDecimal currentPrice;

    public MiningService(long createHeight, int awardingCycle, int rewardHalvingCycle, int decimals, BigDecimal initialPrice) {
        this.createHeight = createHeight;
        this.awardingCycle = awardingCycle;
        this.rewardHalvingCycle = rewardHalvingCycle;
        this.decimals = decimals;
        this.initialPrice = initialPrice;
        this.nextRewardHalvingHeight = this.createHeight + this.rewardHalvingCycle;
        this.currentPrice = initialPrice;
    }

    public BigDecimal getInitialPrice() {
        return initialPrice;
    }

    public long getCreateHeight() {
        return createHeight;
    }

    /**
     * Get the current price
     *
     * @param currentHeight current height
     * @return
     */
    public BigDecimal getCurrentPrice(long currentHeight) {
        BigDecimal price = this.initialPrice;

        //Is there a record in the reward cycle queue?
        if (!this.totalDepositList.isEmpty()) {
            int currentCycle = this.calcRewardCycle(currentHeight);
            //Check whether the current reward cycle is in the index
            if (!this.totalDepositIndex.containsKey(currentCycle)) {
                //If not, a reward record for the current reward cycle is generated
                moveLastDepositToCurrentCycle(currentHeight);
            }
            //Get the latest reward cycle record
            RewardCycleInfo cycleInfoTmp = this.totalDepositList.get(this.totalDepositList.size() - 1);
            BigInteger intAmount = cycleInfoTmp.getDepositAmount();
            if (intAmount.compareTo(BigInteger.ZERO) != 0) {
                String amount = toNuls(intAmount).toString();
                BigDecimal bigAmount = new BigDecimal(amount);
                price = cycleInfoTmp.getCurrentPrice().divide(bigAmount, decimals, BigDecimal.ROUND_DOWN);
            }
        } else {
            //If there is no record of the reward cycle, the half-cycle price is calculated.
            if (this.rewardHalvingCycle > 0 && this.nextRewardHalvingHeight <= currentHeight) {
                price = calcHalvingPrice(currentHeight);
            }
        }
        return price;
    }

    public List<RewardCycleInfo> getTotalDepositList() {
        return this.totalDepositList;
    }

    public int getAwardingCycle() {
        return awardingCycle;
    }

    public int getRewardHalvingCycle() {
        return rewardHalvingCycle;
    }

    public Map<String, MiningInfo> getMingUsers() {
        return mingUsers;
    }

    /**
     * Initialization of mining information
     *
     * @param currentHeight    current height
     * @param miningAddress    mining address
     * @param depositorAddress depositor address
     * @param depositNumber    deposit number
     */
    public void initMingInfo(long currentHeight, String miningAddress, String depositorAddress, long depositNumber) {
        MiningDetailInfo mingDetailInfo = new MiningDetailInfo(miningAddress, depositorAddress, depositNumber);
        int currentRewardCycle = this.calcRewardCycle(currentHeight);
        mingDetailInfo.setNextStartMiningCycle(currentRewardCycle + 2);
        MiningInfo mingInfo = mingUsers.get(miningAddress);
        //The address for the first time in mining
        if (mingInfo == null) {
            mingInfo = new MiningInfo();
            mingInfo.getMiningDetailInfos().put(depositNumber, mingDetailInfo);
            mingUsers.put(miningAddress, mingInfo);
        } else {
            mingInfo.getMiningDetailInfos().put(depositNumber, mingDetailInfo);
        }
    }

    public MiningInfo getMiningInfo(String userAddress) {
        return mingUsers.get(userAddress);
    }

    /**
     * Delete single mortgage mining information
     *
     * @param userAddress   user address
     * @param depositNumber deposit number
     */
    public void removeMiningInfo(String userAddress, long depositNumber) {
        MiningInfo miningInfo = mingUsers.get(userAddress);
        miningInfo.removeMiningDetailInfoByNumber(depositNumber);
        if (miningInfo.getMiningDetailInfos().size() == 0) {
            mingUsers.remove(userAddress);
        }
    }

    /**
     * Delete all mining information from users
     *
     * @param depositInfo Mortgage information
     */
    public void removeAllMiningInfo(DepositInfo depositInfo) {
        Map<Long, DepositDetailInfo> depositDetailInfos = depositInfo.getDepositDetailInfos();
        for (Long key : depositDetailInfos.keySet()) {
            DepositDetailInfo detailInfo = depositDetailInfos.get(key);
            MiningInfo miningInfo = mingUsers.get(detailInfo.getMiningAddress());
            miningInfo.removeMiningDetailInfoByNumber(detailInfo.getDepositNumber());
            if (miningInfo.getMiningDetailInfos().size() == 0) {
                mingUsers.remove(detailInfo.getMiningAddress());
            }
        }
    }

    /**
     * Add the amount of the mortgage to the queue when joining the mortgage
     *
     * @param depositValue  the amount of mortgage
     * @param currentHeight current height
     */
    public void putDeposit(BigInteger depositValue, long currentHeight) {
        int currentCycle = this.calcRewardCycle(currentHeight);
        //Check whether the total number of mortgages in the next incentive cycle is in the queue
        if (!totalDepositIndex.containsKey(currentCycle + 1)) {
            moveLastDepositToCurrentCycle(currentHeight + this.awardingCycle);
        }
        int putCycle = currentCycle + 2;

        boolean isContainsKey = totalDepositIndex.containsKey(putCycle);
        RewardCycleInfo cycleInfo = new RewardCycleInfo();
        if (!isContainsKey) {
            //Calculate the price of halving the reward
            long rewardingHeight = putCycle * this.awardingCycle + this.createHeight;
            if (this.rewardHalvingCycle > 0 && this.nextRewardHalvingHeight <= rewardingHeight) {
                this.currentPrice = this.currentPrice.divide(PocmUtil.HLAVING, decimals, BigDecimal.ROUND_DOWN);
                this.nextRewardHalvingHeight += this.rewardHalvingCycle;
            }

            //First addition of reward cycle records
            if (this.lastCalcCycle == 0) {
                cycleInfo.setDepositAmount(depositValue);
                cycleInfo.setRewardingCylce(putCycle);
                cycleInfo.setDifferCycleValue(1);
                cycleInfo.setCurrentPrice(this.currentPrice);
                this.totalDepositList.add(cycleInfo);
            } else {
                RewardCycleInfo lastCycleInfo = this.totalDepositList.get(this.totalDepositIndex.get(this.lastCalcCycle));
                cycleInfo.setDepositAmount(depositValue.add(lastCycleInfo.getDepositAmount()));
                cycleInfo.setRewardingCylce(putCycle);
                cycleInfo.setDifferCycleValue(putCycle - lastCycleInfo.getRewardingCylce());
                cycleInfo.setCurrentPrice(this.currentPrice);
                this.totalDepositList.add(cycleInfo);
            }
            this.totalDepositIndex.put(putCycle, this.totalDepositList.size() - 1);
            this.lastCalcCycle = putCycle;
        } else {
            int alreadyTotalDepositIndex = this.totalDepositIndex.get(putCycle);
            RewardCycleInfo cycleInfoTmp = this.totalDepositList.get(alreadyTotalDepositIndex);
            cycleInfoTmp.setDepositAmount(depositValue.add(cycleInfoTmp.getDepositAmount()));
        }
    }

    /**
     * Exit the reward cycle queue
     *
     * @param detailInfo    Mortgage detailed information
     * @param currentHeight current height
     */
    public void quitDeposit(DepositDetailInfo detailInfo, long currentHeight) {
        //Amount of Mortgage Withdrawal
        BigInteger depositValue = detailInfo.getDepositAmount();
        int currentCycle = calcRewardCycle(currentHeight);
        int depositCycle = calcRewardCycle(detailInfo.getDepositHeight());

        if (currentCycle == depositCycle) {
            //When the mortgage and mortgage exit in the same reward cycle, update the total mortgage a reward cycle number
            RewardCycleInfo cycleInfoTmp = totalDepositList.get(this.totalDepositIndex.get(currentCycle + 2));
            cycleInfoTmp.setDepositAmount(cycleInfoTmp.getDepositAmount().subtract(depositValue));
        } else {
            //Update the total number of mortgages in the current incentive cycle when joining and withdrawing mortgages are not in the same cycle
            int operCycle = currentCycle + 1;
            boolean isContainsKey = this.totalDepositIndex.containsKey(operCycle);

            if (isContainsKey) {
                //The reward cycle index already contains the reward cycle to be operated on.
                RewardCycleInfo cycleInfoTmp = this.totalDepositList.get(this.totalDepositIndex.get(operCycle));
                cycleInfoTmp.setDepositAmount(cycleInfoTmp.getDepositAmount().subtract(depositValue));
            } else {
                //The current height has reached the reward halving height, and all reward heights corresponding to the halving cycle height are added to the queue.
                long nextHeight = currentHeight + awardingCycle;
                if (rewardHalvingCycle > 0 && nextRewardHalvingHeight <= nextHeight) {
                    this.moveLastDepositToHalvingCycle(nextRewardHalvingHeight, nextHeight);
                }

                RewardCycleInfo cycleInfo = new RewardCycleInfo();

                //Get the latest information about the reward cycle from the reward cycle queue
                RewardCycleInfo cycleInfoTmp = this.totalDepositList.get(this.totalDepositList.size() - 1);
                cycleInfo.setDepositAmount(cycleInfoTmp.getDepositAmount().subtract(depositValue));
                cycleInfo.setDifferCycleValue(operCycle - cycleInfoTmp.getRewardingCylce());
                cycleInfo.setCurrentPrice(currentPrice);
                cycleInfo.setRewardingCylce(operCycle);

                //Add reward cycle information to the queue and update the index
                this.totalDepositList.add(cycleInfo);
                totalDepositIndex.put(operCycle, this.totalDepositList.size() - 1);
                this.lastCalcCycle = operCycle;
            }
        }
    }


    /**
     * When the amount of mortgage remains unchanged, the information of the incentive cycle at the height of the half-cycle of the incentive is added to the queue.
     *
     * @param startRewardHalvingHeight Half the Starting Height
     * @param currentHeight            current height
     */
    private void moveLastDepositToHalvingCycle(long startRewardHalvingHeight, long currentHeight) {
        int rewardingCycle = this.lastCalcCycle;
        long height = startRewardHalvingHeight;
        while (height <= currentHeight) {
            RewardCycleInfo cycleInfo = new RewardCycleInfo();
            this.currentPrice = currentPrice.divide(PocmUtil.HLAVING, decimals, BigDecimal.ROUND_DOWN);
            rewardingCycle = calcRewardCycle(height);
            calcRewardCycle(height);
            boolean isContainsKey = this.totalDepositIndex.containsKey(rewardingCycle);
            if (isContainsKey) {
                continue;
            }
            if (this.lastCalcCycle != 0) {
                RewardCycleInfo cycleInfoTmp = this.totalDepositList.get(this.totalDepositIndex.get(this.lastCalcCycle));
                cycleInfo.setDepositAmount(cycleInfoTmp.getDepositAmount());
                cycleInfo.setDifferCycleValue(rewardingCycle - cycleInfoTmp.getRewardingCylce());
            } else {
                //First mortgage operation
                cycleInfo.setDepositAmount(BigInteger.ZERO);
                cycleInfo.setDifferCycleValue(1);
            }
            cycleInfo.setRewardingCylce(rewardingCycle);
            cycleInfo.setCurrentPrice(this.currentPrice);
            this.totalDepositList.add(cycleInfo);
            this.totalDepositIndex.put(rewardingCycle, this.totalDepositList.size() - 1);
            height += this.rewardHalvingCycle;
            this.lastCalcCycle = rewardingCycle;
        }
        this.nextRewardHalvingHeight = height;
    }


    /**
     * Calculate the amount of reward
     *
     * @param depositInfo Mortgage information
     * @param mingResult  Mining results
     * @return
     */
    public BigInteger calcMining(DepositInfo depositInfo, Map<String, BigInteger> mingResult) {
        BigInteger mining = BigInteger.ZERO;
        long currentHeight = Block.number();
        int currentRewardCycle = this.calcRewardCycle(currentHeight);
        //Update the mortgage information of the previous incentive cycle to the mortgage information of the current incentive cycle
        this.moveLastDepositToCurrentCycle(currentHeight);

        Map<Long, DepositDetailInfo> detailInfos = depositInfo.getDepositDetailInfos();
        for (Long key : detailInfos.keySet()) {
            DepositDetailInfo detailInfo = detailInfos.get(key);
            BigInteger miningTmp = BigInteger.ZERO;

            MiningInfo miningInfo = mingUsers.get(detailInfo.getMiningAddress());
            MiningDetailInfo mingDetailInfo = miningInfo.getMiningDetailInfoByNumber(detailInfo.getDepositNumber());
            int nextStartMiningCycle = mingDetailInfo.getNextStartMiningCycle();
            //Not reaching the height of reward
            if (nextStartMiningCycle > currentRewardCycle) {
                continue;
            }
            BigDecimal sumPrice = this.calcPriceBetweenCycle(nextStartMiningCycle);
            BigDecimal depositAmountNULS = toNuls(detailInfo.getDepositAmount());
            miningTmp = miningTmp.add(depositAmountNULS.multiply(sumPrice).scaleByPowerOfTen(decimals).toBigInteger());

            mingDetailInfo.setMiningAmount(mingDetailInfo.getMiningAmount().add(miningTmp));
            mingDetailInfo.setMiningCount(mingDetailInfo.getMiningCount() + currentRewardCycle - nextStartMiningCycle + 1);
            mingDetailInfo.setNextStartMiningCycle(currentRewardCycle + 1);
            miningInfo.setTotalMining(miningInfo.getTotalMining().add(miningTmp));
            miningInfo.setReceivedMining(miningInfo.getReceivedMining().add(miningTmp));

            if (mingResult.containsKey(mingDetailInfo.getReceiverMiningAddress())) {
                miningTmp = mingResult.get(mingDetailInfo.getReceiverMiningAddress()).add(miningTmp);
            }
            mingResult.put(mingDetailInfo.getReceiverMiningAddress(), miningTmp);
            mining = mining.add(miningTmp);
        }
        return mining;
    }

    /**
     * Add the current high reward cycle to the queue
     *
     * @param currentHeight current height
     */
    private void moveLastDepositToCurrentCycle(long currentHeight) {
        int currentCycle = this.calcRewardCycle(currentHeight);
        //If the current high reward cycle is in the queue, exit the method directly.
        if (this.totalDepositIndex.containsKey(currentCycle)) {
            return;
        } else {
            //The current height has reached the reward halving height, and all reward information corresponding to the halving period height is added to the queue.
            if (this.rewardHalvingCycle > 0 && this.nextRewardHalvingHeight <= currentHeight) {
                this.moveLastDepositToHalvingCycle(nextRewardHalvingHeight, currentHeight);
            }
        }

        //At this point, check if the current high reward cycle is in the queue.
        if (!this.totalDepositIndex.containsKey(currentCycle)) {
            RewardCycleInfo cycleInfo = new RewardCycleInfo();
            RewardCycleInfo cycleInfoTmp;
            if (!this.totalDepositList.isEmpty()) {
                //Get information about the last reward cycle in the queue
                cycleInfoTmp = this.totalDepositList.get(this.totalDepositList.size() - 1);
                cycleInfo.setDepositAmount(cycleInfoTmp.getDepositAmount());
                cycleInfo.setDifferCycleValue(currentCycle - cycleInfoTmp.getRewardingCylce());
                cycleInfo.setCurrentPrice(this.currentPrice);
                cycleInfo.setRewardingCylce(currentCycle);
            } else {
                cycleInfo.setDepositAmount(BigInteger.ZERO);
                cycleInfo.setDifferCycleValue(1);
                cycleInfo.setCurrentPrice(this.currentPrice);
                cycleInfo.setRewardingCylce(currentCycle);
            }
            lastCalcCycle = currentCycle;
            this.totalDepositList.add(cycleInfo);
            this.totalDepositIndex.put(currentCycle, this.totalDepositList.size() - 1);
        }
    }

    /**
     * Calculate the sum of incentive prices from the specified incentive cycle
     *
     * @param startCycle the start the reward cycle
     * @return
     */
    private BigDecimal calcPriceBetweenCycle(int startCycle) {
        BigDecimal sumPrice = BigDecimal.ZERO;
        BigDecimal sumPriceForRegin = BigDecimal.ZERO;
        int startIndex = this.totalDepositIndex.get(startCycle - 1) + 1;
        for (int i = startIndex; i < this.totalDepositList.size(); i++) {
            RewardCycleInfo cycleInfoTmp = this.totalDepositList.get(i);
            String amount = toNuls(cycleInfoTmp.getDepositAmount()).toString();
            if (!"0".equals(amount)) {
                BigDecimal bigAmount = new BigDecimal(amount);
                sumPrice = cycleInfoTmp.getCurrentPrice().divide(bigAmount, this.decimals, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(cycleInfoTmp.getDifferCycleValue()));
            }
            sumPriceForRegin = sumPriceForRegin.add(sumPrice);
        }
        return sumPriceForRegin;
    }

    /**
     * Calculate the reward period for the current height
     *
     * @param currentHeight current height
     * @return
     */
    public int calcRewardCycle(long currentHeight) {
        return Integer.parseInt(String.valueOf(currentHeight - this.createHeight)) / this.awardingCycle;
    }

    /**
     * Calculate the unit price of the half-cycle, the maximum allowable 90 times of the half-cycle
     *
     * @param currentHeight
     * @return
     */
    private BigDecimal calcHalvingPrice(long currentHeight) {
        int rewardHalvingRound = Integer.parseInt(String.valueOf(currentHeight - this.createHeight - 1)) / this.rewardHalvingCycle;
        BigDecimal round = BigDecimal.ZERO;
        int count = rewardHalvingRound / 30;
        BigDecimal base = new BigDecimal(2 << 29);
        if (count == 0 || rewardHalvingRound == 30) {
            round = new BigDecimal(2 << rewardHalvingRound - 1);
        } else if (count == 1 || rewardHalvingRound == 60) {
            round = new BigDecimal(2 << rewardHalvingRound - 31);
            round = base.multiply(round);
        } else if (count == 2 || rewardHalvingRound == 90) {
            round = new BigDecimal(2 << rewardHalvingRound - 61);
            round = base.multiply(base).multiply(round);
        } else {
            require(false, "The maximum allowable number of halving cycles is 90 times, which has reached " + rewardHalvingRound + " times.");
            return BigDecimal.ZERO;
        }
        return this.initialPrice.divide(round, decimals, BigDecimal.ROUND_DOWN);

    }

}
