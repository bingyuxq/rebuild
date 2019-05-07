/*
rebuild - Building your business-systems freely.
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

package com.rebuild.web.common;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.rebuild.server.helper.QiniuCloud;
import com.rebuild.server.helper.SysConfiguration;
import com.rebuild.utils.AppUtils;

import cn.devezhao.commons.web.ServletUtils;

/**
 * 文件上传
 * 
 * @author zhaofang123@gmail.com
 * @since 11/06/2017
 */
public class FileUploader extends HttpServlet {
	private static final long serialVersionUID = 5264645972230896850L;
	
	private static final Log LOG = LogFactory.getLog(FileUploader.class);
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		String uploadName = null;
		try {
			List<FileItem> fileItems = parseFileItem(request);
			for (FileItem item : fileItems) {
				uploadName = item.getName();
				if (uploadName == null) {
					continue;
				}
				
				uploadName = QiniuCloud.formatFileKey(uploadName);
				File file = null;
				// 上传临时文件
				if ("1".equals(request.getParameter("temp"))) {
					uploadName = uploadName.split("/")[2];
					file = SysConfiguration.getFileOfTemp(uploadName);
				} else {
					file = SysConfiguration.getFileOfData(uploadName);
					FileUtils.forceMkdir(file.getParentFile());
				}
				
				item.write(file);
				if (!file.exists()) {
					ServletUtils.writeJson(response, AppUtils.formatControllMsg(1000, "上传失败"));
					return;
				}
				break;
			}
			
		} catch (Exception e) {
			LOG.error(null, e);
			uploadName = null;
		}
		
		if (uploadName != null) {
			ServletUtils.writeJson(response, AppUtils.formatControllMsg(0, uploadName));
		} else {
			ServletUtils.writeJson(response, AppUtils.formatControllMsg(1000, "上传失败"));
		}
	}
	
	// ----
	
	private FileItemFactory fileItemFactory;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		File track = SysConfiguration.getFileOfTemp("track");
		if (!track.exists() || !track.isDirectory()) {
			boolean mked = track.mkdir();
			if (!mked) {
				throw new ExceptionInInitializerError("Could't mkdir track repository");
			}
		}
		fileItemFactory = new DiskFileItemFactory(
				DiskFileItemFactory.DEFAULT_SIZE_THRESHOLD * 2/*20MB*/, track);
	}
	
	/*-
	 * 读取上传的文件列表
	 */
	private List<FileItem> parseFileItem(HttpServletRequest request) throws Exception {
		if (!ServletFileUpload.isMultipartContent(request)) {
			return Collections.<FileItem>emptyList();
		}
		
		ServletFileUpload upload = new ServletFileUpload(this.fileItemFactory);
		List<FileItem> files = null;
		try {
			files = upload.parseRequest(request);
		} catch (Exception ex) {
			if (ex instanceof IOException || ex.getCause() instanceof IOException) {
				LOG.warn("I/O, 传输意外中断, 客户端取消???", ex);
				return Collections.<FileItem>emptyList();
			}
			throw ex;
		}
		
		if (files == null || files.isEmpty()) {
			return Collections.<FileItem>emptyList();
		}
		return files;
	}
}
