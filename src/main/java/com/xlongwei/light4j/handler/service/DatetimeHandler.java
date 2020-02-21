package com.xlongwei.light4j.handler.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.time.FastDateFormat;

import com.alibaba.fastjson.JSONObject;
import com.networknt.utility.StringUtils;
import com.xlongwei.light4j.handler.ServiceHandler.AbstractHandler;
import com.xlongwei.light4j.util.DateUtil;
import com.xlongwei.light4j.util.HandlerUtil;
import com.xlongwei.light4j.util.HolidayUtil;
import com.xlongwei.light4j.util.IdWorker.SystemClock;
import com.xlongwei.light4j.util.JsonUtil;
import com.xlongwei.light4j.util.NumberUtil;
import com.xlongwei.light4j.util.RedisConfig;
import com.xlongwei.light4j.util.StringUtil;
import com.xlongwei.light4j.util.ZhDate;

import cn.hutool.core.date.Week;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * datetime handler
 * @author xlongwei
 *
 */
public class DatetimeHandler extends AbstractHandler {
	private static FastDateFormat fastDateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");
	
	public DatetimeHandler() {
		reload();
	}

	private void reload() {
		String holidays = RedisConfig.get("datetime.holidays");
		if(StringUtil.isBlank(holidays)) {
			return;
		}else {
			JSONObject json = JsonUtil.parseNew(holidays);
			for(String key : json.keySet()) {
				HolidayUtil.addPlan(key, json.getString(key));
			}
		}
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {
		String path = exchange.getAttachment(AbstractHandler.PATH);
		if(StringUtils.isBlank(path)) {
			exchange.getResponseHeaders().add(new HttpString("Content-Type"), "application/json");
			HandlerUtil.setCorsHeaders(exchange);
	        exchange.setStatusCode(200);
	        String datetime = fastDateFormat.format(SystemClock.now());
			exchange.getResponseSender().send("{\"datetime\":\""+datetime+"\"}");
			return;
		}
		Date day = DateUtil.parseNow(HandlerUtil.getParam(exchange, "day")), start = null, end = null;
		Map<String, Object> map = new HashMap<>(4);
		switch(path) {
		case "isworkday":
			map.put("isworkday", HolidayUtil.isworkday(day));
			break;
		case "isholiday":
			map.put("isholiday", HolidayUtil.isholiday(day));
			break;
		case "isweekend":
			map.put("isweekend", HolidayUtil.isweekend(day));
			break;
		case "nextworkday":
			day = HolidayUtil.nextworkday(day, NumberUtil.parseBoolean(HandlerUtil.getParam(exchange, "skipweekend"), false));
			map.put("nextworkday", DateUtil.dateFormat.format(day));
			break;
		case "offsetworkday":
			day = HolidayUtil.offsetworkday(day, NumberUtil.parseInt(HandlerUtil.getParam(exchange, "offset"), 0), NumberUtil.parseBoolean(HandlerUtil.getParam(exchange, "skipweekend"), false));
			map.put("offsetworkday", DateUtil.dateFormat.format(day));
			break;
		case "offsetday":
			day = cn.hutool.core.date.DateUtil.offsetDay(day, NumberUtil.parseInt(HandlerUtil.getParam(exchange, "offset"), 0));
			map.put("offsetday", DateUtil.dateFormat.format(day));
			break;
		case "holiday":
			holiday(exchange, day, map);
			break;
		case "age":
			map.put("age", cn.hutool.core.date.DateUtil.age(DateUtil.parseNow(HandlerUtil.getParam(exchange, "birth")), day));
			break;
		case "betweenday":
		case "betweenworkday":
			start = DateUtil.parse(HandlerUtil.getParam(exchange, "start")); end = DateUtil.parse(HandlerUtil.getParam(exchange, "end"));
			if(start==null && end==null) {
				start = cn.hutool.core.date.DateUtil.beginOfYear(day);
			}
			start = start==null ? day : start;
			end = end==null ? day : end;
			long between = "betweenday".equals(path) ? cn.hutool.core.date.DateUtil.betweenDay(start, end, true)
					: HolidayUtil.betweenworkday(start, end, NumberUtil.parseBoolean(HandlerUtil.getParam(exchange, "skipweekend"), false));
			map.put(path, between);
			map.put("start", DateUtil.dayFormat.format(start));
			map.put("end", DateUtil.dayFormat.format(end));
			break;
		case "workday":
			workday(exchange, day, map);
			break;
		case "nongli":
			convert(exchange, day, map);
			break;
		case "yangli":
			convert(exchange, null, map);
			break;
		case "convert":
			convert(exchange, StringUtil.isBlank(HandlerUtil.getParam(exchange, "lunarYear")) ? day : null, map);
			break;
		default:
			break;
		}
		HandlerUtil.setResp(exchange, map);
	}

	private void holiday(HttpServerExchange exchange, Date day, Map<String, Object> map) {
		String name = HandlerUtil.getParam(exchange, "name");
		String plan = StringUtil.isBlank(name) ? null : HolidayUtil.plans.get(DateUtil.yearFormat.format(day)+"."+name);
		if(plan==null) {
			Integer flag = HolidayUtil.holidays.get(HolidayUtil.dateFormat.format(day));
			if(flag!=null && flag.intValue()>0) {
				name = HolidayUtil.nameOf(flag);
				plan = StringUtil.isBlank(name) ? null : HolidayUtil.plans.get(DateUtil.yearFormat.format(day)+"."+name);
			}
		}
		if(StringUtil.hasLength(plan)) {
			map.put("holiday", name);
			map.put("remark", plan);
		}
	}

	private void workday(HttpServerExchange exchange, Date day, Map<String, Object> map) {
		Date start;
		Date end;
		String type = HandlerUtil.getParam(exchange, "type");
		map.put("day", DateUtil.dateFormat.format(day));
		if("isworkday".equals(type)) {
			map.put("isworkday", HolidayUtil.isworkday(day));
		}else if("isholiday".equals(type)) {
			map.put("isholiday", HolidayUtil.isholiday(day));
		}else if("isweekend".equals(type)) {
			map.put("isweekend", HolidayUtil.isweekend(day));
		}else if("nextworkday".equals(type)) {
			day = HolidayUtil.nextworkday(day, NumberUtil.parseBoolean(HandlerUtil.getParam(exchange, "skipweekend"), false));
			map.put("day", DateUtil.dateFormat.format(day));
		}else if("offsetworkday".equals(type)) {
			day = HolidayUtil.offsetworkday(day, NumberUtil.parseInt(HandlerUtil.getParam(exchange, "offset"), 0), NumberUtil.parseBoolean(HandlerUtil.getParam(exchange, "skipweekend"), false));
			map.put("day", DateUtil.dateFormat.format(day));
		}else if("offsetday".equals(type)) {
			day = cn.hutool.core.date.DateUtil.offsetDay(day, NumberUtil.parseInt(HandlerUtil.getParam(exchange, "offset"), 0));
			map.put("day", DateUtil.dateFormat.format(day));
		}else if("countday".equals(type) || "countworkday".equals(type)) {
			start = DateUtil.parse(HandlerUtil.getParam(exchange, "start")); end = DateUtil.parse(HandlerUtil.getParam(exchange, "end"));
			if(start==null && end==null) {
				start = cn.hutool.core.date.DateUtil.beginOfYear(day);
			}
			start = start==null ? day : start;
			end = end==null ? day : end;
			map.put("start", DateUtil.dayFormat.format(start));
			map.put("end", DateUtil.dayFormat.format(end));
			map.put("count", "countday".equals(type) ? cn.hutool.core.date.DateUtil.betweenDay(start, end, true)
					: HolidayUtil.betweenworkday(start, end, NumberUtil.parseBoolean(HandlerUtil.getParam(exchange, "skipweekend"), false)));
		}else if("reload".equals(type)){
			reload();
			map.put("reload", HolidayUtil.holidays.size());
		}else if("nongli".equals(type)){
			convert(exchange, day, map);
		}else if("yangli".equals(type)){
			convert(exchange, null, map);
		}else if("convert".equals(type)){
			convert(exchange, StringUtil.isBlank(HandlerUtil.getParam(exchange, "lunarYear")) ? day : null, map);
		}else {
			map.put("isworkday", HolidayUtil.isworkday(day));
			map.put("isholiday", HolidayUtil.isholiday(day));
			Week week = cn.hutool.core.date.DateUtil.dayOfWeekEnum(day);
			map.put("week", week.getValue()==1 ? 7 : week.getValue()-1);
			map.put("isweekend", week==Week.SATURDAY || week==Week.SUNDAY);
		}
	}

	private void convert(HttpServerExchange exchange, Date day, Map<String, Object> map) {
		if(day!=null) {
			ZhDate zhDate = ZhDate.fromDate(day);
			map.put("nongli", zhDate.toString());
			map.put("chinese", zhDate.chinese());
			map.put("ganzhi", zhDate.ganzhi());
			map.put("shengxiao", zhDate.shengxiao());
			map.put("lunarYear", zhDate.getLunarYear());
			map.put("lunarMonth", zhDate.getLunarMonth());
			map.put("lunarDay", zhDate.getLunarDay());
			map.put("isLeapMonth", zhDate.isLeapMonth());
		}else {
			int lunarYear = NumberUtil.parseInt(HandlerUtil.getParam(exchange, "lunarYear"), 2020);
			int lunarMonth = NumberUtil.parseInt(HandlerUtil.getParam(exchange, "lunarMonth"), 1);
			int lunarDay = NumberUtil.parseInt(HandlerUtil.getParam(exchange, "lunarDay"), 28);
			boolean isLeapMonth = NumberUtil.parseBoolean(HandlerUtil.getParam(exchange, "isLeapMonth"), false);
			if(ZhDate.validate(lunarYear, lunarMonth, lunarDay, isLeapMonth)) {
				ZhDate zhDate = new ZhDate(lunarYear, lunarMonth, lunarDay, isLeapMonth);
				day = zhDate.toDate();
				map.put("day", DateUtil.dateFormat.format(day));
				map.put("chinese", zhDate.chinese());
				map.put("ganzhi", zhDate.ganzhi());
				map.put("shengxiao", zhDate.shengxiao());
			}else {
				map.put("status", "农历日期不支持");
			}
		}
	}
}
