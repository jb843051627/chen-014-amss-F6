package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TAmssOutCard;
import com.fc.v2.model.auto.TAmssOutCardStage;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 缴回卡过口动作的回执。
 *
 * <p>动没动成一律以本回执为准：成功 success=true 且带回更新后的卡与新落的履历笔；
 * 被拒 success=false 并在 message 里说清缘由（例如起第二张活卡时，
 * 要说明这台车压在哪一段）。页面摆的、经办人自己认的均不作数。
 *
 * @author fuce
 * @date 2026-09-29
 */
@ApiModel(value = "OutCardActionResult", description = "缴回卡过口动作回执")
public class OutCardActionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "是否动成")
    private boolean success;

    @ApiModelProperty(value = "被拒缘由（成功时为空）")
    private String message;

    @ApiModelProperty(value = "动作后的卡")
    private TAmssOutCard card;

    @ApiModelProperty(value = "本次新落的履历笔（幂等未动笔时为空）")
    private TAmssOutCardStage stage;

    public static OutCardActionResult ok(TAmssOutCard card, TAmssOutCardStage stage) {
        OutCardActionResult r = new OutCardActionResult();
        r.success = true;
        r.card = card;
        r.stage = stage;
        return r;
    }

    public static OutCardResultBuilder reject(String message) {
        return new OutCardResultBuilder(message);
    }

    /** 被拒回执构造器：可带回当前卡，便于把车压在哪一段说清 */
    public static class OutCardResultBuilder {
        private final String message;
        private TAmssOutCard card;

        OutCardResultBuilder(String message) {
            this.message = message;
        }

        public OutCardResultBuilder withCard(TAmssOutCard card) {
            this.card = card;
            return this;
        }

        public OutCardActionResult build() {
            OutCardActionResult r = new OutCardActionResult();
            r.success = false;
            r.message = this.message;
            r.card = this.card;
            return r;
        }
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public TAmssOutCard getCard() {
        return card;
    }

    public void setCard(TAmssOutCard card) {
        this.card = card;
    }

    public TAmssOutCardStage getStage() {
        return stage;
    }

    public void setStage(TAmssOutCardStage stage) {
        this.stage = stage;
    }
}
