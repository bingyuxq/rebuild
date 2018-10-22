/*
rebuild - Building your system freely.
Copyright (C) 2018 devezhao <zhaofang123@gmail.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package com.rebuild.web.base.entity;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.alibaba.fastjson.JSON;
import com.rebuild.server.Application;
import com.rebuild.server.entityhub.EasyMeta;
import com.rebuild.server.helper.manager.FieldValueWrapper;
import com.rebuild.server.helper.manager.ViewTabManager;
import com.rebuild.server.metadata.EntityHelper;
import com.rebuild.server.metadata.MetadataHelper;
import com.rebuild.utils.JSONUtils;
import com.rebuild.web.BaseControll;
import com.rebuild.web.LayoutConfig;

import cn.devezhao.commons.CalendarUtils;
import cn.devezhao.commons.ObjectUtils;
import cn.devezhao.commons.web.ServletUtils;
import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.Field;
import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.dialect.FieldType;
import cn.devezhao.persist4j.engine.ID;

/**
 * 相关项列表
 * 
 * @author devezhao
 * @since 10/22/2018
 */
@Controller
@RequestMapping("/app/")
public class RelatedListControll extends BaseControll implements LayoutConfig {

	@RequestMapping("entity/related-list")
	public void relatedList(HttpServletRequest request, HttpServletResponse response) throws IOException {
		ID masterId = getIdParameterNotNull(request, "master");
		String related = getParameterNotNull(request, "related");
		
		Entity relatedEntity = MetadataHelper.getEntity(related);
		String sql = genMasterSql(masterId, relatedEntity, false);
		
		int pn = NumberUtils.toInt(getParameter(request, "pageNo"), 1);
		int ps = NumberUtils.toInt(getParameter(request, "pageSize"), 200);
		
		Object[][] array = Application.createQuery(sql).setLimit(ps, pn * ps - ps).array();
		for (Object[] o : array) {
			o[0] = o[0].toString();
			o[1] = FieldValueWrapper.wrapFieldValue(o[1], relatedEntity.getNameField());
			if (StringUtils.EMPTY == o[1]) {
				o[1] = o[0].toString().toUpperCase();  // 使用ID值作为名称字段值
			}
			o[2] = CalendarUtils.getUTCDateTimeFormat().format(o[2]);
		}
		
		JSON ret = JSONUtils.toJSONObject(
				new String[] { "total", "data" },
				new Object[] { 0, array });
		writeSuccess(response, ret);
	}
	
	@RequestMapping("entity/related-counts")
	public void relatedCounts(HttpServletRequest request, HttpServletResponse response) throws IOException {
		ID masterId = getIdParameterNotNull(request, "master");
		String relates[] = getParameterNotNull(request, "relates").split(",");
		
		Map<String, Integer> countMap = new HashMap<>();
		for (String related : relates) {
			String sql = genMasterSql(masterId, MetadataHelper.getEntity(related), true);
			Object[] count = Application.createQuery(sql).unique();
			countMap.put(related, ObjectUtils.toInt(count[0]));
		}
		writeSuccess(response, countMap);
	}
	
	/**
	 * @param masterId
	 * @param relatedEntity
	 * @param count
	 * @return
	 */
	static String genMasterSql(ID masterId, Entity relatedEntity, boolean count) {
		Entity masterEntity = MetadataHelper.getEntity(masterId.getEntityCode());
		Set<String> relatedFields = new HashSet<>();
		for (Field field : relatedEntity.getFields()) {
			if (field.getType() == FieldType.REFERENCE 
					&& ArrayUtils.contains(field.getReferenceEntities(), masterEntity)) {
				relatedFields.add(field.getName() + " = ''{0}''");
			}
		}
		
		String masterSql = "(" + StringUtils.join(relatedFields, " or ") + ")";
		masterSql = MessageFormat.format(masterSql, masterId);
		
		String baseSql = "select %s from " + relatedEntity.getName() + " where " + masterSql;
		
		Field primaryField = relatedEntity.getPrimaryField();
		Field namedField = relatedEntity.getNameField();
		
		if (count) {
			baseSql = String.format(baseSql, "count(" + primaryField.getName() + ")");
		} else {
			baseSql = String.format(baseSql, primaryField.getName() + "," + namedField.getName() + "," + EntityHelper.modifiedOn);
			baseSql += " order by " + namedField.getName() + " asc";
		}
		return baseSql;
	}
	
	// --
	
	@RequestMapping(value = "{entity}/viewtab-settings", method = RequestMethod.POST)
	@Override
	public void sets(@PathVariable String entity,
			HttpServletRequest request, HttpServletResponse response) throws IOException {
		ID user = getRequestUser(request);
		JSON config = ServletUtils.getRequestJson(request);
		
		Object[] vtab = ViewTabManager.getRaw(entity);
		Record record = null;
		if (vtab == null) {
			record = EntityHelper.forNew(EntityHelper.ViewTabConfig, user);
			record.setString("belongEntity", entity);
		} else {
			record = EntityHelper.forUpdate((ID) vtab[0], user);
		}
		record.setString("config", config.toJSONString());
		Application.getCommonService().createOrUpdate(record);
		
		writeSuccess(response);
	}
	
	@RequestMapping(value = "{entity}/viewtab-settings", method = RequestMethod.GET)
	@Override
	public void gets(@PathVariable String entity,
			HttpServletRequest request, HttpServletResponse response) throws IOException {
		Object[] vtab = ViewTabManager.getRaw(entity);
		
		Entity entityMeta = MetadataHelper.getEntity(entity);
		Set<String[]> refs = new HashSet<>();
		for (Field field : entityMeta.getReferenceToFields()) {
			Entity e = field.getOwnEntity();
			refs.add(new String[] { e.getName(), EasyMeta.getLabel(e) });
		}
		
		JSON ret = JSONUtils.toJSONObject(
				new String[] { "config", "refs" },
				new Object[] { vtab == null ? null : vtab[1], refs });
		writeSuccess(response, ret);
	}
}