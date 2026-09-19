package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.ReadCursor;

public interface ReadCursorRepositoryCustom {

    ReadCursor upsert(ReadCursor cursor);
}
