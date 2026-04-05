package com.university.awards.file.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.university.awards.file.entity.SysFile;

/**
 * 文件元数据服务。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>仅管理 {@code sys_file} 元数据的 CRUD。</li>
 *   <li>物理文件的保存/白名单校验/previewType 判定由 {@link FileStorageService} 负责。</li>
 * </ul>
 */
public interface SysFileService extends IService<SysFile> {
}

