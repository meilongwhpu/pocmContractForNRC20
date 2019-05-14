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
     * 初始价格，每个奖励周期所有的NULS抵押数平分XX个token
     */
    private BigDecimal initialPrice;

    /**
     * 合约创建高度
     */
    private long createHeight;

    /**
     * 奖励发放周期（参数类型为数字，每过XXXX块发放一次）
     */
    private int awardingCycle;

    /**
     * 奖励减半周期（可选参数，若选择，则参数类型为数字，每XXXXX块奖励减半）
     */
    private int rewardHalvingCycle;

    /**
     * 精度
     */
    private int decimals;

    /**
     * 用户挖矿信息(key为接收挖矿Token地址）
     */
    private Map<String, MiningInfo> mingUsers = new HashMap<String, MiningInfo>();

    /**
     * 每个奖励周期的抵押金额索引，k-v：奖励周期-List序号
     */
    private Map<Integer, Integer> totalDepositIndex=new LinkedHashMap<Integer, Integer>();

    /**
     * 抵押金额列表，与索引表联合使用
     */
    private List<RewardCycleInfo> totalDepositList =new LinkedList<RewardCycleInfo>();

    /**
     * 下一次奖励减半的高度
     */
    private long nextRewardHalvingHeight;

    /**
     * 上一次抵押数量有变动的奖励周期
     */
    private int lastCalcCycle = 0;

    /**
     * 当前价格，当前奖励周期所有的NULS抵押数平分XX个token
     */
    private BigDecimal currentPrice;

    public MiningService(long createHeight, int awardingCycle, int rewardHalvingCycle, int decimals, BigDecimal initialPrice) {
        this.createHeight = createHeight;
        this.awardingCycle = awardingCycle;
        this.rewardHalvingCycle = rewardHalvingCycle;
        this.decimals = decimals;
        this.initialPrice = initialPrice;
        this.nextRewardHalvingHeight = this.createHeight + this.rewardHalvingCycle;
        this.currentPrice=initialPrice;
    }

    public BigDecimal getinitialPrice() {
        return initialPrice;
    }

    public long getCreateHeight() {
        return createHeight;
    }

    public BigDecimal getCurrentPrice(long currentHeight) {
        BigDecimal price = this.initialPrice;
        if (!this.totalDepositList.isEmpty()) {
            int currentCycle = this.calcRewardCycle(currentHeight);
            //检查当前奖励周期的总抵押数是否在队列中
            if (!this.totalDepositIndex.containsKey(currentCycle)) {
                moveLastDepositToCurrentCycle(currentHeight);
            }
            RewardCycleInfo cycleInfoTmp = this.totalDepositList.get(this.totalDepositList.size() - 1);
            BigInteger intAmount = cycleInfoTmp.getDepositAmount();
            if (intAmount.compareTo(BigInteger.ZERO) != 0) {
                String amount = toNuls(intAmount).toString();
                BigDecimal bigAmount = new BigDecimal(amount);
                price = cycleInfoTmp.getCurrentPrice().divide(bigAmount, decimals, BigDecimal.ROUND_DOWN);
            }
        }else {
            if (this.rewardHalvingCycle > 0 && this.nextRewardHalvingHeight <= currentHeight) {
                price =calcHalvingPrice(currentHeight);
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
     * 初始化挖矿信息
     *
     * @param miningAddress
     * @param depositorAddress
     * @param depositNumber
     * @return
     */
    public void initMingInfo(long currentHeight, String miningAddress, String depositorAddress, long depositNumber) {
        MiningDetailInfo mingDetailInfo = new MiningDetailInfo(miningAddress, depositorAddress, depositNumber);
        int currentRewardCycle = this.calcRewardCycle(currentHeight);
        mingDetailInfo.setNextStartMiningCycle(currentRewardCycle + 2);
        MiningInfo mingInfo = mingUsers.get(miningAddress);
        //该Token地址为第一次挖矿
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
     * 删除单笔抵押的挖矿信息
     *
     * @param userAddress
     */
    public void removeMiningInfo(String userAddress, long depositNumber) {
        MiningInfo miningInfo = mingUsers.get(userAddress);
        miningInfo.removeMiningDetailInfoByNumber(depositNumber);
        if (miningInfo.getMiningDetailInfos().size() == 0) {
            mingUsers.remove(userAddress);
        }
    }

    /**
     * 删除用户的所有挖矿信息
     *
     * @param depositInfo
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
     * 在加入抵押时将抵押金额加入队列中
     *
     * @param depositValue
     * @param currentHeight
     */
    public void putDeposit(BigInteger depositValue, long currentHeight) {
        int currentCycle = this.calcRewardCycle(currentHeight);
        //检查下一个奖励周期的总抵押数是否在队列中
        if (!totalDepositIndex.containsKey(currentCycle + 1)) {
            moveLastDepositToCurrentCycle(currentHeight + this.awardingCycle);
        }
        int putCycle = currentCycle + 2;

        boolean isContainsKey = totalDepositIndex.containsKey(putCycle);
        RewardCycleInfo cycleInfo = new RewardCycleInfo();
        if (!isContainsKey) {
            //计算奖励减半
            long rewardingHeight = putCycle * this.awardingCycle + this.createHeight;
            if (this.rewardHalvingCycle > 0 && this.nextRewardHalvingHeight <= rewardingHeight) {
                this.currentPrice = this.currentPrice.divide(PocmUtil.HLAVING, decimals, BigDecimal.ROUND_DOWN);
                this.nextRewardHalvingHeight += this.rewardHalvingCycle;
            }

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
     * @param detailInfo
     * @param currentHeight
     */
    public void quitDeposit(DepositDetailInfo detailInfo, long currentHeight) {
        //从队列中退出抵押金额
        BigInteger depositValue = detailInfo.getDepositAmount();
        int currentCycle = calcRewardCycle(currentHeight);
        int depositCycle = calcRewardCycle(detailInfo.getDepositHeight());

        if (currentCycle == depositCycle) {
            //加入抵押和退出抵押在同一个奖励周期，更新下一个奖励周期的总抵押数
            RewardCycleInfo cycleInfoTmp = totalDepositList.get(this.totalDepositIndex.get(currentCycle + 2));
            cycleInfoTmp.setDepositAmount(cycleInfoTmp.getDepositAmount().subtract(depositValue));
        } else {
            //加入抵押和退出抵押不在同一个奖励周期,则更新当前奖励周期的总抵押数
            int operCycle = currentCycle + 1;
            boolean isContainsKey = this.totalDepositIndex.containsKey(operCycle);
            //待操作的奖励周期已经计算了总抵押数
            if (isContainsKey) {
                RewardCycleInfo cycleInfoTmp = this.totalDepositList.get(this.totalDepositIndex.get(operCycle));
                cycleInfoTmp.setDepositAmount(cycleInfoTmp.getDepositAmount().subtract(depositValue));
            } else {
                //当前高度已经达到奖励减半高度,将所有的减半周期高度对于的奖励高度加入队列
                long nextHeight = currentHeight + awardingCycle;
                if (rewardHalvingCycle > 0 && nextRewardHalvingHeight <= nextHeight) {
                    this.moveLastDepositToHalvingCycle(nextRewardHalvingHeight, nextHeight);
                }

                RewardCycleInfo cycleInfo = new RewardCycleInfo();

                //取队列中最后一个奖励周期的信息
                RewardCycleInfo cycleInfoTmp = this.totalDepositList.get(this.totalDepositList.size() - 1);
                cycleInfo.setDepositAmount(cycleInfoTmp.getDepositAmount().subtract(depositValue));
                cycleInfo.setDifferCycleValue(operCycle - cycleInfoTmp.getRewardingCylce());
                cycleInfo.setCurrentPrice(currentPrice);
                cycleInfo.setRewardingCylce(operCycle);
                this.totalDepositList.add(cycleInfo);

                totalDepositIndex.put(operCycle, this.totalDepositList.size() - 1);
                this.lastCalcCycle = operCycle;
            }
        }

    }

    /**
     * 抵押数额没有变动的情况下，将奖励减半周期所在高度的奖励周期抵押数加入队列
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
                //第一次进行抵押操作
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
     * 计算奖励数额
     *
     * @param depositInfo
     * @param mingResult
     * @return
     */
    public BigInteger calcMining(DepositInfo depositInfo, Map<String, BigInteger> mingResult) {
        BigInteger mining = BigInteger.ZERO;
        long currentHeight = Block.number();
        int currentRewardCycle = this.calcRewardCycle(currentHeight);
        //将上一个奖励周期的总抵押数更新至当前奖励周期的总抵押数
        this.moveLastDepositToCurrentCycle(currentHeight);

        Map<Long, DepositDetailInfo> detailInfos = depositInfo.getDepositDetailInfos();
        for (Long key : detailInfos.keySet()) {
            DepositDetailInfo detailInfo = detailInfos.get(key);
            BigInteger miningTmp = BigInteger.ZERO;

            MiningInfo miningInfo = mingUsers.get(detailInfo.getMiningAddress());
            MiningDetailInfo mingDetailInfo = miningInfo.getMiningDetailInfoByNumber(detailInfo.getDepositNumber());
            int nextStartMiningCycle = mingDetailInfo.getNextStartMiningCycle();
            //说明未到领取奖励的高度
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
     * 将当前高度的奖励周期加入队列
     *
     * @param currentHeight
     */
    private void moveLastDepositToCurrentCycle(long currentHeight) {
        int currentCycle = this.calcRewardCycle(currentHeight);
        //若当前高度的奖励周期在队列中，则直接退出此方法
        if (this.totalDepositIndex.containsKey(currentCycle)) {
            return;
        } else {
            //当前高度已经达到奖励减半高度,将所有的减半周期高度对于的奖励高度加入队列
            if (this.rewardHalvingCycle > 0 && this.nextRewardHalvingHeight <= currentHeight) {
                this.moveLastDepositToHalvingCycle(nextRewardHalvingHeight, currentHeight);
            }
        }

        //此时再检查是否当前高度的奖励周期在队列中
        if (!this.totalDepositIndex.containsKey(currentCycle)) {
            RewardCycleInfo cycleInfo = new RewardCycleInfo();
            RewardCycleInfo cycleInfoTmp;
            if (!this.totalDepositList.isEmpty()) {
                //取队列中最后一个奖励周期的信息
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
     * 从指定的奖励周期开始计算奖励价格之和
     *
     * @param startCycle
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
     * 计算当前高度所在的奖励周期
     *
     * @param currentHeight 当前高度
     * @return
     */
    public int calcRewardCycle(long currentHeight) {
        return Integer.parseInt(String.valueOf(currentHeight - this.createHeight)) / this.awardingCycle;
    }

    /**
     * 计算减半周期的单价,减半周期最大允许90次
     * @param currentHeight
     * @return
     */
    private BigDecimal calcHalvingPrice(long currentHeight){
        int rewardHalvingRound= Integer.parseInt(String.valueOf(currentHeight-this.createHeight-1))/this.rewardHalvingCycle;
        BigDecimal round=BigDecimal.ZERO;
        int count=rewardHalvingRound/30;
        BigDecimal base=new BigDecimal(2<<29);
        if(count==0||rewardHalvingRound==30){
            round=new BigDecimal(2<<rewardHalvingRound-1);
        }else if(count==1||rewardHalvingRound==60){
            round=new BigDecimal(2<<rewardHalvingRound-31);
            round =base.multiply(round);
        }else if(count==2||rewardHalvingRound==90){
            round=new BigDecimal(2<<rewardHalvingRound-61);
            round =base.multiply(base).multiply(round);
        }else{
            require(false, "减半周期次数最大允许90次,目前已经达到"+rewardHalvingRound+"次");
            return BigDecimal.ZERO;
        }
        return this.initialPrice.divide(round,decimals,BigDecimal.ROUND_DOWN);

    }

}
