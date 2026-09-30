package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.model.auto.TAmssOutCard;
import com.fc.v2.model.auto.TAmssOutLine;
import com.fc.v2.model.custom.OutCardAdvanceBo;
import com.fc.v2.model.custom.OutCardAuditVo;
import com.fc.v2.model.custom.OutCardOpenResultVo;
import com.fc.v2.model.custom.OutCardReportVo;
import com.fc.v2.service.ITAmssOutCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 变型拖拉机牌证缴回事务卡 Controller（state-machine 形状：流转入口）。
 * 页上只摆情况、不动卡：起卡、过口、退回、缘由、翻履历，全走业务服务层那对方法，
 * 动没动成以服务层给回为准，挡回原因原样摆给经办人。
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
        QueryWrapper<TAmssOutCard> queryWrapper = new QueryWrapper<TAmssOutCard>();
        queryWrapper.eq("del_flag", 0).orderByDesc("create_time");
        if (record.getVehNo() != null && !record.getVehNo().trim().isEmpty()) {
            queryWrapper.eq("veh_no", record.getVehNo().trim());
        }
        if (record.getBizNo() != null && !record.getBizNo().trim().isEmpty()) {
            queryWrapper.like("biz_no", record.getBizNo().trim());
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
    @ApiOperation(value = "起一张活卡", notes = "一车一卡，从收牌段起；第二张活卡当场挡回")
    @PostMapping("/open")
    @RequiresPermissions("amssOutCard:open")
    @ResponseBody
    public AjaxResult open(String vehNo) {
        TAmssOutCard card = amssOutCardService.open(vehNo);
        return AjaxResult.success("已起卡 " + card.getBizNo()).put("data", card);
    }

    @Log(title = "变型拖拉机牌证缴回事务卡批量起卡", action = "openBatch")
    @ApiOperation(value = "月初一回起几十张卡", notes = "车号逗号隔开；重复、空号收进驳回清单")
    @PostMapping("/openBatch")
    @RequiresPermissions("amssOutCard:open")
    @ResponseBody
    public AjaxResult openBatch(String vehNos) {
        List<String> nos = Arrays.asList(ConvertUtil.toStrArray(vehNos));
        OutCardOpenResultVo result = amssOutCardService.openBatch(nos);
        return AjaxResult.success("起成 " + result.getOpened().size() + " 张，挡回 "
                + result.getRejected().size() + " 辆").put("data", result);
    }

    @Log(title = "变型拖拉机牌证缴回事务卡推进", action = "advance")
    @ApiOperation(value = "推进一档（老用法，不带料）", notes = "只在不补料的段口走得通：清账了结口")
    @PostMapping("/advance")
    @RequiresPermissions("amssOutCard:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return toAjax(amssOutCardService.advance(id, remark) != null ? 1 : 0);
    }

    @Log(title = "变型拖拉机牌证缴回事务卡验车过口", action = "advanceCheck")
    @ApiOperation(value = "验车段带三样过口", notes = "号牌实收几枚、行驶证与检验在册情况、未结认定罚款是否已清")
    @PostMapping("/advanceCheck")
    @RequiresPermissions("amssOutCard:advance")
    @ResponseBody
    public AjaxResult advanceCheck(Long id, Integer plateQty, String licenseStatus,
                                   Integer claimClear, String remark) {
        OutCardAdvanceBo bo = new OutCardAdvanceBo();
        bo.setPlateQty(plateQty);
        bo.setLicenseStatus(licenseStatus);
        bo.setClaimClear(claimClear);
        TAmssOutCard card = amssOutCardService.advance(id, bo, remark);
        return AjaxResult.success("过口已成，卡此刻往下一段走").put("data", card);
    }

    @Log(title = "变型拖拉机牌证缴回事务卡回退", action = "rollback")
    @ApiOperation(value = "回退一档（老用法）", notes = "清账段退回验车段重来")
    @PostMapping("/rollback")
    @RequiresPermissions("amssOutCard:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return toAjax(amssOutCardService.rollback(id, remark) != null ? 1 : 0);
    }

    @Log(title = "变型拖拉机牌证缴回事务卡退回验车", action = "rollbackByQty")
    @ApiOperation(value = "清账段拿当场实收对枚数", notes = "与验车记的枚数不合才退，合着不退")
    @PostMapping("/rollbackByQty")
    @RequiresPermissions("amssOutCard:rollback")
    @ResponseBody
    public AjaxResult rollbackByQty(Long id, Integer actualPlateQty, String remark) {
        TAmssOutCard card = amssOutCardService.rollback(id, actualPlateQty, remark);
        return AjaxResult.success("两处枚数不合，已退回验车段重来，旧说法沉底").put("data", card);
    }

    @Log(title = "变型拖拉机牌证缴回事务卡缘由续记", action = "reason")
    @ApiOperation(value = "清账段续记缴回缘由", notes = "1报废 2灭失 3转外省 4主动交回；改一次续一笔，旧笔原样搁着")
    @PostMapping("/reason")
    @RequiresPermissions("amssOutCard:advance")
    @ResponseBody
    public AjaxResult reason(Long id, String reasonCode, String remark) {
        TAmssOutLine line = amssOutCardService.addReason(id, reasonCode, remark);
        return AjaxResult.success("缘由已续第 " + line.getVerNo() + " 笔").put("data", line);
    }

    @ApiOperation(value = "翻缴回履历", notes = "从收牌往了结一笔一笔摆，只许翻不许碰")
    @GetMapping("/lines")
    @RequiresPermissions("amssOutCard:view")
    @ResponseBody
    public AjaxResult lines(Long id) {
        return AjaxResult.successData(200, amssOutCardService.listLines(id));
    }

    @ApiOperation(value = "抽卡核验", notes = "从了结往回收牌逐笔对，多缺补改抽到一样就算没走成")
    @GetMapping("/audit")
    @RequiresPermissions("amssOutCard:audit")
    @ResponseBody
    public AjaxResult audit(Long id) {
        OutCardAuditVo vo = amssOutCardService.audit(id);
        AjaxResult json = vo.isPassed()
                ? AjaxResult.success("逐笔对得上")
                : AjaxResult.error("抽到 " + vo.getProblems().size() + " 处对不上");
        json.put("data", vo);
        return json;
    }

    @ApiOperation(value = "年终清零口径", notes = "当年起卡数=已了结+在办半路，全站只一种口径")
    @GetMapping("/report")
    @RequiresPermissions("amssOutCard:audit")
    @ResponseBody
    public AjaxResult report(@RequestParam(defaultValue = "0") int year) {
        int y = year > 0 ? year : java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        OutCardReportVo vo = amssOutCardService.yearReport(y);
        AjaxResult json = vo.isBalanced()
                ? AjaxResult.success("两栏合上当年起卡数")
                : AjaxResult.error("两栏合不上当年起卡数");
        json.put("data", vo);
        return json;
    }
}
