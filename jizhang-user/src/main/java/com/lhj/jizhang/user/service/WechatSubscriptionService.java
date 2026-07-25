package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.client.WechatNotificationClient;
import com.lhj.jizhang.user.config.WechatNotificationProperties;
import com.lhj.jizhang.user.dto.WechatSubscriptionInDTO;
import com.lhj.jizhang.user.dto.WechatSubscriptionOutDTO;
import com.lhj.jizhang.user.entity.NotificationOutboxEntity;
import com.lhj.jizhang.user.entity.UserAuthEntity;
import com.lhj.jizhang.user.entity.WechatSubscriptionEntity;
import com.lhj.jizhang.user.mapper.NotificationOutboxMapper;
import com.lhj.jizhang.user.mapper.UserAuthMapper;
import com.lhj.jizhang.user.mapper.WechatSubscriptionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class WechatSubscriptionService {
    private static final List<String> SCENES = List.of("RECURRING_PENDING", "BUDGET_ALERT");
    private final WechatSubscriptionMapper subscriptionMapper;
    private final NotificationOutboxMapper outboxMapper;
    private final UserAuthMapper authMapper;
    private final WechatNotificationClient client;
    private final WechatNotificationProperties properties;
    private final ObjectMapper objectMapper;

    public WechatSubscriptionService(WechatSubscriptionMapper subscriptionMapper,
                                     NotificationOutboxMapper outboxMapper, UserAuthMapper authMapper,
                                     WechatNotificationClient client, WechatNotificationProperties properties,
                                     ObjectMapper objectMapper) {
        this.subscriptionMapper = subscriptionMapper; this.outboxMapper = outboxMapper; this.authMapper = authMapper;
        this.client = client; this.properties = properties; this.objectMapper = objectMapper;
    }

    public List<WechatSubscriptionOutDTO> settings(Long userId) {
        return SCENES.stream().map(scene -> output(find(userId, scene), scene)).toList();
    }

    @Transactional
    public WechatSubscriptionOutDTO update(Long userId, WechatSubscriptionInDTO input) {
        WechatSubscriptionEntity entity = find(userId, input.scene());
        if (entity == null) {
            entity = new WechatSubscriptionEntity(); entity.setUserId(userId); entity.setScene(input.scene());
            entity.setAuthorizedCount(0); entity.setCreator(String.valueOf(userId));
        }
        entity.setEnabledFlag(input.enabled() ? 1 : 0);
        if (input.enabled() && input.authorized()) {
            entity.setAuthorizedCount(entity.getAuthorizedCount() + 1);
            entity.setLastAuthorizedTime(LocalDateTime.now(ZoneOffset.UTC));
        }
        entity.setModifier(String.valueOf(userId));
        if (entity.getId() == null) subscriptionMapper.insert(entity); else subscriptionMapper.updateById(entity);
        return output(entity, input.scene());
    }

    @Transactional
    public void enqueue(String eventKey, Long userId, String scene, String pagePath, Map<String, Object> parameters) {
        WechatSubscriptionEntity subscription = find(userId, scene);
        String templateId = template(scene);
        if (subscription == null || subscription.getEnabledFlag() == 0 || subscription.getAuthorizedCount() <= 0
                || !properties.enabled() || templateId == null || templateId.isBlank()) return;
        NotificationOutboxEntity item = new NotificationOutboxEntity();
        item.setEventKey(eventKey); item.setUserId(userId); item.setScene(scene); item.setTemplateId(templateId);
        item.setPagePath(pagePath); item.setStatus("PENDING"); item.setRetryCount(0);
        item.setNextRetryTime(LocalDateTime.now(ZoneOffset.UTC)); item.setExpiredTime(LocalDateTime.now(ZoneOffset.UTC).plusDays(7));
        try { item.setParametersJson(objectMapper.writeValueAsString(parameters)); outboxMapper.insert(item); }
        catch (DuplicateKeyException ignored) { }
        catch (Exception exception) { throw new BusinessException(ErrorCodes.INTERNAL_ERROR, "通知任务创建失败"); }
    }

    @Scheduled(fixedDelayString = "${app.wechat.notification.scan-interval-ms:60000}")
    public void sendDue() {
        if (!properties.enabled()) return;
        List<NotificationOutboxEntity> items = outboxMapper.selectList(Wrappers.<NotificationOutboxEntity>lambdaQuery()
                .in(NotificationOutboxEntity::getStatus, "PENDING", "RETRY")
                .le(NotificationOutboxEntity::getNextRetryTime, LocalDateTime.now(ZoneOffset.UTC))
                .gt(NotificationOutboxEntity::getExpiredTime, LocalDateTime.now(ZoneOffset.UTC))
                .orderByAsc(NotificationOutboxEntity::getId).last("LIMIT 20"));
        for (NotificationOutboxEntity item : items) sendOne(item);
    }

    @Transactional
    protected void sendOne(NotificationOutboxEntity item) {
        WechatSubscriptionEntity subscription = find(item.getUserId(), item.getScene());
        if (subscription == null || subscription.getEnabledFlag() == 0 || subscription.getAuthorizedCount() <= 0) {
            fail(item, "NO_AUTHORIZATION", false); return;
        }
        UserAuthEntity auth = authMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getUserId, item.getUserId()).eq(UserAuthEntity::getProvider, "WECHAT")
                .eq(UserAuthEntity::getStatus, 1));
        if (auth == null || auth.getOpenid() == null) { fail(item, "NO_WECHAT_IDENTITY", false); return; }
        try {
            Map<String, Object> data = objectMapper.readValue(item.getParametersJson(), new TypeReference<>() {});
            WechatNotificationClient.SendResult result = client.send(auth.getOpenid(), item.getTemplateId(), item.getPagePath(), data);
            if (result.success()) {
                item.setStatus("SENT"); item.setFailureCode(null); outboxMapper.updateById(item);
                subscription.setAuthorizedCount(Math.max(0, subscription.getAuthorizedCount() - 1));
                subscriptionMapper.updateById(subscription);
            } else fail(item, "WX_" + result.errorCode(), result.errorCode() == -1 || result.errorCode() >= 50000);
        } catch (Exception exception) { fail(item, "NETWORK_ERROR", true); }
    }

    private void fail(NotificationOutboxEntity item, String code, boolean retryable) {
        int retries = item.getRetryCount() + 1; item.setRetryCount(retries); item.setFailureCode(code);
        if (retryable && retries < 3) {
            item.setStatus("RETRY"); item.setNextRetryTime(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(retries * 5L));
        } else item.setStatus("FAILED");
        outboxMapper.updateById(item);
    }

    private WechatSubscriptionEntity find(Long userId, String scene) {
        return subscriptionMapper.selectOne(Wrappers.<WechatSubscriptionEntity>lambdaQuery()
                .eq(WechatSubscriptionEntity::getUserId, userId).eq(WechatSubscriptionEntity::getScene, scene));
    }
    private WechatSubscriptionOutDTO output(WechatSubscriptionEntity entity, String scene) {
        return new WechatSubscriptionOutDTO(scene, entity != null && entity.getEnabledFlag() == 1,
                entity == null ? 0 : entity.getAuthorizedCount(), template(scene), properties.enabled());
    }
    private String template(String scene) { return "RECURRING_PENDING".equals(scene)
            ? properties.recurringTemplateId() : properties.budgetTemplateId(); }
}
