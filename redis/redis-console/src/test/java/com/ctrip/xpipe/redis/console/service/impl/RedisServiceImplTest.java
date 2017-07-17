package com.ctrip.xpipe.redis.console.service.impl;

import com.ctrip.xpipe.redis.console.constant.XPipeConsoleConstant;
import com.ctrip.xpipe.redis.console.model.RedisTbl;
import com.ctrip.xpipe.redis.console.model.ShardModel;
import com.ctrip.xpipe.redis.console.service.exception.ResourceNotFoundException;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.unidal.dal.jdbc.DalException;
import org.unidal.tuple.Pair;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @author wenchao.meng
 *         <p>
 *         Mar 31, 2017
 */
public class RedisServiceImplTest extends AbstractServiceImplTest{


    @Autowired
    private RedisServiceImpl redisService;

    private String dcName;
    private String shardName;

    @Before
    public void beforeRedisServiceImplTest(){
        dcName = dcNames[0];
        shardName = shardNames[0];

    }

    @Test
    public void testInsertRedises() throws ResourceNotFoundException, DalException {

        List<RedisTbl> redises = redisService.findRedisesByDcClusterShard(dcName, clusterName, shardName);

        redisService.insertInstances(redises.get(0).getDcClusterShardId(), XPipeConsoleConstant.ROLE_REDIS, new Pair<>("127.0.0.1", randomInt()), new Pair<>("127.0.0.1", randomInt()));

        List<RedisTbl> newRedises = redisService.findRedisesByDcClusterShard(dcName, clusterName, shardName);

        Assert.assertEquals(redises.size() + 2, newRedises.size());

    }

    @Test
    public void testInsertKeepers() throws ResourceNotFoundException, DalException {

        List<RedisTbl> keepers = redisService.findKeepersByDcClusterShard(dcName, clusterName, shardName);

        redisService.insertInstances(keepers.get(0).getDcClusterShardId(), XPipeConsoleConstant.ROLE_KEEPER, new Pair<>("127.0.0.1", randomInt()), new Pair<>("127.0.0.1", randomInt()));

        List<RedisTbl> newKeepers = redisService.findKeepersByDcClusterShard(dcName, clusterName, shardName);

        Assert.assertEquals(keepers.size() + 2, newKeepers.size());

    }

    @Test
    public void testDeleteKeepers() throws ResourceNotFoundException, DalException {

        List<RedisTbl> keepers = redisService.findKeepersByDcClusterShard(dcName, clusterName, shardName);
        Assert.assertTrue(keepers.size() > 0);

        redisService.deleteKeepers(dcName, clusterName, shardName);

        keepers = redisService.findKeepersByDcClusterShard(dcName, clusterName, shardName);
        Assert.assertEquals(0, keepers.size());
    }

    @Test
    public void testDelete() throws ResourceNotFoundException, DalException {

        List<RedisTbl> redises = redisService.findRedisesByDcClusterShard(dcName, clusterName, shardName);

        redisService.delete(redises.toArray(new RedisTbl[0]));

        List<RedisTbl> newRedises = redisService.findRedisesByDcClusterShard(dcName, clusterName, shardName);

        Assert.assertEquals(0, newRedises.size());

    }

    @Test
    public void testFindByRole() throws ResourceNotFoundException {

        List<RedisTbl> allByDcClusterShard = redisService.findAllByDcClusterShard(dcName, clusterName, shardName);
        checkAllInstances(allByDcClusterShard);

        List<RedisTbl> keepers = redisService.findKeepersByDcClusterShard(dcName, clusterName, shardName);
        List<RedisTbl> redises = redisService.findRedisesByDcClusterShard(dcName, clusterName, shardName);

        Assert.assertEquals(allByDcClusterShard.size(), keepers.size() + redises.size());

        keepers.forEach(new Consumer<RedisTbl>() {
            @Override
            public void accept(RedisTbl redisTbl) {
                logger.debug("[keeper]{}", redisTbl);
                Assert.assertEquals(XPipeConsoleConstant.ROLE_KEEPER, redisTbl.getRedisRole());
            }
        });

        redises.forEach(new Consumer<RedisTbl>() {
            @Override
            public void accept(RedisTbl redisTbl) {
                logger.debug("[redis]{}", redisTbl);
                Assert.assertEquals(XPipeConsoleConstant.ROLE_REDIS, redisTbl.getRedisRole());
            }
        });

    }

    @Test
    public void testBatchUpdate() throws ResourceNotFoundException {

        List<RedisTbl> allByDcClusterShard = redisService.findAllByDcClusterShard(dcName, clusterName, shardName);
        checkAllInstances(allByDcClusterShard);

        for (RedisTbl redisTbl : allByDcClusterShard){
            if(redisTbl.getRedisRole().equalsIgnoreCase(XPipeConsoleConstant.ROLE_REDIS)){
                redisTbl.setMaster(!redisTbl.isMaster());
            }
        }

        redisService.batchUpdate(allByDcClusterShard);

        List<RedisTbl> newAll = redisService.findAllByDcClusterShard(dcName, clusterName, shardName);
        checkAllInstances(newAll);

        for(RedisTbl newRedis : newAll){
            for(RedisTbl oldRedis : allByDcClusterShard){
                if(newRedis.getId() == oldRedis.getId()){
                    logger.info("old:{}", oldRedis);
                    logger.info("new:{}", newRedis);
                    Assert.assertEquals(oldRedis.isMaster(), newRedis.isMaster());
                }
            }
        }

    }


    @Test
    public void testUpdateRedises() throws IOException, ResourceNotFoundException {


        List<RedisTbl> allByDcClusterShard = redisService.findAllByDcClusterShard(dcName, clusterName, shardName);
        checkAllInstances(allByDcClusterShard);
        boolean firstSlave = true;
        RedisTbl newMaster = null;

        for(RedisTbl redisTbl : allByDcClusterShard){
            if(redisTbl.getRedisRole().equals(XPipeConsoleConstant.ROLE_REDIS)){

                if(redisTbl.isMaster()){
                    redisTbl.setMaster(false);
                }else if(!redisTbl.isMaster() && firstSlave){
                    redisTbl.setMaster(true);
                    newMaster = redisTbl;
                    firstSlave = false;
                }

            }
        }

        checkAllInstances(allByDcClusterShard);

        ShardModel shardModel = new ShardModel(allByDcClusterShard);
        redisService.updateRedises(dcName, clusterName, shardName, shardModel);

        allByDcClusterShard = redisService.findAllByDcClusterShard(dcName, clusterName, shardName);
        checkAllInstances(allByDcClusterShard);

        Stream<RedisTbl> redisTblStream = allByDcClusterShard.stream().filter(instance -> instance.isMaster());

        RedisTbl  currentMaster = redisTblStream.findFirst().get();
        Assert.assertEquals(newMaster.getId(), currentMaster.getId());

    }

    private void checkAllInstances(List<RedisTbl> allByDcClusterShard) {

        Assert.assertEquals(4, allByDcClusterShard.size());

        int masterCount = 0;

        for(RedisTbl redisTbl : allByDcClusterShard){
            logger.debug("{}", redisTbl);
            if(redisTbl.isMaster()){
                masterCount++;
            }
        }
        Assert.assertEquals(1, masterCount);

    }

    @Test
    public void testSub(){

        List<Pair<String, Integer>> first = Lists.newArrayList(
                Pair.from("127.0.0.1", 1111),
                Pair.from("127.0.0.1", 1112),
                Pair.from("127.0.0.1", 1113)
        );

        List<RedisTbl> second = Lists.newArrayList(
                new RedisTbl().setRedisIp("127.0.0.1").setRedisPort(1111),
                new RedisTbl().setRedisIp("127.0.0.1").setRedisPort(1112),
                new RedisTbl().setRedisIp("127.0.0.1").setRedisPort(9999)
        );

        List<Pair<String, Integer>> sub = redisService.sub(first, second);
        Assert.assertEquals(1, sub.size());
    }

    @Test
    public void testInter(){

        List<Pair<String, Integer>> first = Lists.newArrayList(
                Pair.from("127.0.0.1", 1111),
                Pair.from("127.0.0.1", 1112),
                Pair.from("127.0.0.1", 1113)
        );

        List<RedisTbl> second = Lists.newArrayList(
                new RedisTbl().setRedisIp("127.0.0.1").setRedisPort(1111),
                new RedisTbl().setRedisIp("127.0.0.1").setRedisPort(1112),
                new RedisTbl().setRedisIp("127.0.0.1").setRedisPort(9999)
        );

        List<RedisTbl> inter = redisService.inter(first, second);
        Assert.assertEquals(2, inter.size());
    }


}