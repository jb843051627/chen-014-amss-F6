package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TAmssOutCard;
import com.fc.v2.model.custom.OutCardActionResult;
import com.fc.v2.model.custom.OutCardStageData;
import com.fc.v2.model.custom.OutCardYearReport;
import com.fc.v2.service.ITAmssOutCardService;
import com.fc.v2.util.StringUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 变型拖拉机牌证缴回事务卡 Controller（state-machine 形状：流转入口）。
 *
 * <p>过段只走 service 的 advance/rollback 一对出口，这里不设现造一张卡、
 * 就地改一句的地方；动没动成以 service 回执为准，被拒把缘由原话带回。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "变型拖拉机牌证缴回事务卡")
@Controller
@RequestMapping("/amssOutCard")
public class AmssOutCardController extends BaseController {

    private final String prefix = "admin/amssOutCard";

    @Autowired
    private ITAmssOutCardService amssOutCardService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("amssOutCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "变型拖拉机牌证缴回事务卡流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("amssOutCard:list")
    @ResponseBody
    public ResultTable list(TAmssOutCard record) {
        QueryWrapper<TAmssOutCard> queryWrapper = new QueryWrapper<TAmssOutCard>()
                .eq("del_flag", 0)
                .orderByDesc("create_time");
        if (StringUtils.isNotEmpty(record.getVehiclePlateNo())) {
            queryWrapper.like("vehicle_plate_no", record.getVehiclePlateNo());
        }
        if (StringUtils.isNotEmpty(record.getBizNo())) {
            queryWrapper.like("biz_no", record.getBizNo());
        }
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        startPage();
        com.github.pagehelper.PageInfo<TAmssOutCard> page =
                new com.github.pagehelper.PageInfo<TAmssOutCard>(amssOutCardService.selectTAmssOutCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "变型拖拉机牌证缴回事务卡起卡", action = "open")
    @ApiOperation(value = "起卡", notes = "一车一卡，已有活卡当场挡回并报明压在何段")
    @PostMapping("/open")
    @RequiresPermissions("amssOutCard:open")
    @ResponseBody
    public AjaxResult open(String vehiclePlateNo, String bizNo) {
        OutCardActionResult result = amssOutCardService.open(vehiclePlateNo, bizNo);
        if (result.isSuccess()) {
            return success(200, "卡已起，从收牌段走起", result.getCard());
        }
        return error(500, result.getMessage());
    }

    @Log(title = "变型拖拉机牌证缴回事务卡推进", action = "advance")
    @ApiOperation(value = "往前过一段", notes = "只放当前段该走的流程通行，门禁不齐/已了结一律挡回")
    @PostMapping("/advance")
    @RequiresPermissions("amssOutCard:advance")
    @ResponseBody
    public AjaxResult advance(Long id, OutCardStageData data) {
        OutCardActionResult result = amssOutCardService.advance(id, data);
        if (result.isSuccess()) {
            return success(200,
                    StringUtils.isNotEmpty(result.getMessage()) ? result.getMessage() : "过段已成",
                    result);
        }
        return error(500, result.getMessage());
    }

    @Log(title = "变型拖拉机牌证缴回事务卡回退", action = "rollback")
    @ApiOperation(value = "退回上一段", notes = "只退一段，旧说法沉底、重来说法摆明面")
    @PostMapping("/rollback")
    @RequiresPermissions("amssOutCard:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark, String operator) {
        OutCardStageData data = new OutCardStageData();
        data.setRemark(remark);
        data.setOperator(operator);
        OutCardActionResult result = amssOutCardService.rollback(id, data);
        if (result.isSuccess()) {
            return success(200, "已退回上一段，旧说法沉底留档", result);
        }
        return error(500, result.getMessage());
    }

    @Log(title = "变型拖拉机牌证缴回缘由补记", action = "reason")
    @ApiOperation(value = "清账缘由补记", notes = "缘由改一次续一笔，先前各笔原样搁着")
    @PostMapping("/reason")
    @RequiresPermissions("amssOutCard:advance")
    @ResponseBody
    public AjaxResult reason(Long id, String returnReason, String operator) {
        OutCardActionResult result = amssOutCardService.addReason(id, returnReason, operator);
        if (result.isSuccess()) {
            return success(200, "缘由已续一笔，先前各笔原样留档", result);
        }
        return error(500, result.getMessage());
    }

    @ApiOperation(value = "卡详情", notes = "卡当下算走到第几段，以系统照履历推得的为准")
    @GetMapping("/detail")
    @RequiresPermissions("amssOutCard:view")
    @ResponseBody
    public AjaxResult detail(Long id) {
        TAmssOutCard card = amssOutCardService.selectTAmssOutCardById(id);
        if (card == null) {
            return error("卡不存在或已抽走");
        }
        int stage = amssOutCardService.currentStage(id);
        // currentStage/currentStageName 与卡对象一同放进 data，供页面照系统推得的段放行
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("card", card);
        data.put("currentStage", stage);
        data.put("currentStageName", ITAmssOutCardService.STAGE_NAMES[stage]);
        return retobject(200, data);
    }

    @ApiOperation(value = "缴回履历", notes = "一笔接一笔，只许翻看")
    @GetMapping("/history")
    @RequiresPermissions("amssOutCard:view")
    @ResponseBody
    public AjaxResult history(Long id) {
        return retobject(200, amssOutCardService.history(id));
    }

    @ApiOperation(value = "年终报数", notes = "已了结栏 + 走到半路栏 = 当年起的卡数")
    @GetMapping("/summary")
    @RequiresPermissions("amssOutCard:list")
    @ResponseBody
    public AjaxResult summary(Integer year) {
        int y = year == null ? java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) : year;
        OutCardYearReport report = amssOutCardService.yearlyReport(y);
        return retobject(200, report);
    }
}
