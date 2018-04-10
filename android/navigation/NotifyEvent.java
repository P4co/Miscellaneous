package com.roadtrack;

import java.util.List;

public interface NotifyEvent {
    public void notifyEvent(NotifyEventType eventType, boolean onFlag);
}