/*
 * Copyright Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.server.web;

import com.thoughtworks.go.domain.FileHandler;
import com.thoughtworks.go.server.domain.ZippedArtifact;
import com.thoughtworks.go.util.ArtifactLogUtil;
import com.thoughtworks.go.util.GoConstants;
import lombok.experimental.UtilityClass;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.AbstractView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static javax.servlet.http.HttpServletResponse.*;

@UtilityClass
public class FileModelAndView {

    public static ModelAndView createFileView(File file, String sha) {
        boolean hasChanged = isFileChanged(file, sha);
        if (!hasChanged) {
            return new ModelAndView(new AbstractView() {
                @Override
                protected void renderMergedOutputModel(Map<String, Object> model, HttpServletRequest request,
                                                       HttpServletResponse response) throws Exception {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.getWriter().close();
                }
            });
        } else {
            Map<String, Object> model = new HashMap<>();
			if (file instanceof ZippedArtifact) {
				model.put(FileView.NEED_TO_ZIP, true);
			}
            model.put("targetFile", file);
            return new ModelAndView("fileView", model);
        }
    }

    private static boolean isFileChanged(File file, String sha) {
        try {
            String currentHash = FileHandler.sha1Digest(file);
            return !currentHash.equals(sha);
        } catch (Exception e) {
            return true;
        }
    }

    public static ModelAndView forbiddenUrl(String filePath) {
        return ResponseCodeView.create(SC_FORBIDDEN, "Url " + filePath + " contains forbidden characters.");
    }

    public static ModelAndView fileCreated(String filePath) {
        return ResponseCodeView.create(SC_CREATED, "File " + filePath + " was created successfully");
    }

    public static ModelAndView fileAppended(String filePath) {
        return ResponseCodeView.create(SC_OK, "File " + filePath + " was appended successfully");
    }

    public static ModelAndView errorSavingFile(String filePath) {
        return ResponseCodeView.create(SC_INTERNAL_SERVER_ERROR, "Error saving file " + filePath);
    }

    public static ModelAndView errorSavingChecksumFile(String filePath) {
        return ResponseCodeView.create(SC_INTERNAL_SERVER_ERROR, String.format("Error saving checksum file for the artifact at path '%s'", filePath));
    }

    public static ModelAndView invalidUploadRequest() {
        String content = "Invalid request. MultipartFile must have name '" + GoConstants.REGULAR_MULTIPART_FILENAME + "'"
                + " or '" + GoConstants.ZIP_MULTIPART_FILENAME + "' (to automatically unzip stream)";
        return ResponseCodeView.create(SC_BAD_REQUEST, content);
    }

    public static ModelAndView fileNotFound(String filePath) {
        if ((ArtifactLogUtil.getConsoleOutputFolderAndFileName()).equals(filePath)) {
            return ResponseCodeView.create(SC_NOT_FOUND, "Console log for this job is unavailable as it may have been purged by Go or deleted externally.");
        }
        return ResponseCodeView.create(SC_NOT_FOUND, "Artifact '" + filePath + "' is unavailable as it may have been purged by Go or deleted externally.");
    }

    public static ModelAndView fileAlreadyExists(String filePath) {
        return ResponseCodeView.create(SC_FORBIDDEN, "File " + filePath + " already exists.");
    }
}
