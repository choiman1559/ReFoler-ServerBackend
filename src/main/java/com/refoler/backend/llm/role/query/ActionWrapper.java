package com.refoler.backend.llm.role.query;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

public class ActionWrapper {
    @Description("Set of action type to perform")
    public enum ActionTypeImpl {
        @Description("Deletes given file or folder. " +
                "Use \"targetFiles\" to specify exact path, or use \"queryWrapper\" to scope by conditions.")
        OP_DELETE,

        @Description("Create new, empty file. " +
                "Use \"targetFiles\" to specify exact path. \"queryWrapper\" is not used.")
        OP_NEW_FILE,

        @Description("Create new, empty folder. " +
                "Use \"targetFiles\" to specify exact path. \"queryWrapper\" is not used.")
        OP_MAKE_DIR,

        @Description("Copy files or folders into another folder. " +
                "Use \"targetFiles\" or \"queryWrapper\" to specify file/folder to copy, " +
                "and use \"destinationDirectory\" to specify where files copied to.")
        OP_COPY,

        @Description("Move files or folders into another folder. " +
                "Use \"targetFiles\" or \"queryWrapper\" to specify file/folder to move, " +
                "and use \"destinationDirectory\" to specify where files moved to.")
        OP_CUT,

        @Description("Rename file or folder. " +
                "Use \"targetFiles\" to specify what file or folder to rename, and \"destinationDirectory\" to specifies what name (with full path) to change to. " +
                "Note that only first single element of \"targetFiles\" is used in this action.")
        OP_RENAME,
    }

    @Description("Perform various file or folder associated actions. " +
            "Note that all file or folder used by argument must be full path.")
    public static class ActionRequestImpl {
        @Description("Action type to perform. This argument must not be empty.")
        public ActionTypeImpl actionType;

        @Description("Paths of target file or folder")
        public List<String> targetFiles;

        @Description("The destination path to operate on")
        public String destinationDirectory;

        @Description("Specifies query information")
        public QueryWrapper queryWrapper;
    }
}