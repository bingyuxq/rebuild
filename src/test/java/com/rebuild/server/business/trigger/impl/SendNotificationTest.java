/*
Copyright (c) REBUILD <https://getrebuild.com/> and its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.server.business.trigger.impl;

import cn.devezhao.commons.ThreadPool;
import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.engine.ID;
import com.rebuild.server.Application;
import com.rebuild.server.TestSupportWithUser;
import com.rebuild.server.business.trigger.ActionType;
import com.rebuild.server.business.trigger.TriggerWhen;
import com.rebuild.server.configuration.RobotTriggerManager;
import com.rebuild.server.metadata.EntityHelper;
import com.rebuild.server.metadata.MetadataHelper;
import com.rebuild.server.service.bizz.UserService;
import com.rebuild.server.service.configuration.RobotTriggerConfigService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author devezhao-mbp zhaofang123@gmail.com
 * @since 2019/05/29
 */
public class SendNotificationTest extends TestSupportWithUser {

    @Test
    public void testExecute() throws Exception {
        // 添加配置
        Application.getSQLExecutor().execute("delete from robot_trigger_config where BELONG_ENTITY = 'TestAllFields'");

        final ID toUser = SIMPLE_USER;
        final Entity entity = MetadataHelper.getEntity(TEST_ENTITY);

        Record triggerConfig = EntityHelper.forNew(EntityHelper.RobotTriggerConfig, UserService.SYSTEM_USER);
        triggerConfig.setString("belongEntity", "TestAllFields");
        triggerConfig.setInt("when", TriggerWhen.CREATE.getMaskValue() + TriggerWhen.DELETE.getMaskValue());
        triggerConfig.setString("actionType", ActionType.SENDNOTIFICATION.name());
        String content = String.format("{ typy:1, sendTo:['%s'], content:'SENDNOTIFICATION {createdBy} {3782732}' }", toUser);
        triggerConfig.setString("actionContent", content);
        Application.getBean(RobotTriggerConfigService.class).create(triggerConfig);
        RobotTriggerManager.instance.clean(entity);

        // 当前未读消息
        int unread = Application.getNotifications().getUnreadMessage(toUser);

        // 保存/删除会发送两条消息
        Record record = EntityHelper.forNew(entity.getEntityCode(), getSessionUser());
        record.setString("TestAllFieldsName", "SENDNOTIFICATION");
        // Create
        record = Application.getEntityService(entity.getEntityCode()).create(record);
        // Delete
        Application.getEntityService(entity.getEntityCode()).delete(record.getPrimary());

        // 比对消息数
        ThreadPool.waitFor(4000);
        int unreadCheck = Application.getNotifications().getUnreadMessage(toUser);
        assertEquals(unread, unreadCheck - 2);

		// 清理
		Application.getBean(RobotTriggerConfigService.class).delete(triggerConfig.getPrimary());
    }
}
