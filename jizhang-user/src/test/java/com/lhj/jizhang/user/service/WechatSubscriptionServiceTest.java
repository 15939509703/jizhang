package com.lhj.jizhang.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.client.WechatNotificationClient;
import com.lhj.jizhang.user.config.WechatNotificationProperties;
import com.lhj.jizhang.user.entity.NotificationOutboxEntity;
import com.lhj.jizhang.user.mapper.NotificationOutboxMapper;
import com.lhj.jizhang.user.mapper.UserAuthMapper;
import com.lhj.jizhang.user.mapper.WechatSubscriptionMapper;
import com.lhj.jizhang.user.model.BookMenu;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WechatSubscriptionServiceTest {
    private final NotificationOutboxMapper outboxMapper = mock(NotificationOutboxMapper.class);
    private final WechatNotificationClient client = mock(WechatNotificationClient.class);
    private final BookAccessService accessService = mock(BookAccessService.class);
    private final WechatSubscriptionService service = new WechatSubscriptionService(
            mock(WechatSubscriptionMapper.class), outboxMapper, mock(UserAuthMapper.class), client,
            new WechatNotificationProperties(true, "recurring", "budget", "", ""),
            new ObjectMapper(), accessService);

    /** 权限撤销后已排队的通知标记失败且不发送。 */
    @Test
    void revokedAccessBlocksQueuedNotification() {
        NotificationOutboxEntity item = new NotificationOutboxEntity();
        item.setUserId(7L);
        item.setBookId(1L);
        item.setScene("BUDGET_ALERT");
        item.setRetryCount(0);
        doThrow(new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "无权访问", HttpStatus.FORBIDDEN))
                .when(accessService).requireMenu(7L, 1L, BookMenu.BUDGET);

        service.sendOne(item);

        assertEquals("FAILED", item.getStatus());
        assertEquals("ACCESS_REVOKED", item.getFailureCode());
        verify(outboxMapper).updateById(item);
        verify(client, never()).send(any(), any(), any(), any());
    }

    /** 无账本归属的旧通知不能作为内容泄露旁路。 */
    @Test
    void legacyNotificationWithoutBookIsNotSent() {
        service.enqueue("BUDGET_1", 7L, null, "BUDGET_ALERT", "/pages/budget/budget", Map.of());
        verify(outboxMapper, never()).insert(any(NotificationOutboxEntity.class));
    }
}
