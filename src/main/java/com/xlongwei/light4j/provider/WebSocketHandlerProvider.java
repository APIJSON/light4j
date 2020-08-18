package com.xlongwei.light4j.provider;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.resource;
import static io.undertow.Handlers.websocket;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;

import com.networknt.handler.HandlerProvider;
import com.xlongwei.light4j.util.DateUtil;
import com.xlongwei.light4j.util.FileUtil;
import com.xlongwei.light4j.util.IdWorker.SystemClock;
import com.xlongwei.light4j.util.NumberUtil;
import com.xlongwei.light4j.util.RedisConfig;
import com.xlongwei.light4j.util.StringUtil;
import com.xlongwei.light4j.util.TaskUtil;
import com.xlongwei.light4j.util.UploadUtil;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import lombok.extern.slf4j.Slf4j;

/**
 * web socket handler
 * @author xlongwei
 *
 */
@Slf4j
public class WebSocketHandlerProvider implements HandlerProvider {
	private final String key = "ws.chat";
	private final int length = 18;
    @Override
    public HttpHandler getHandler() {
        return path()
                .addPrefixPath("/ws/chat", websocket(chat))
                .addPrefixPath("/ws/ok", ResponseCodeHandler.HANDLE_200)
                .addPrefixPath("/ws/", resource(new ClassPathResourceManager(WebSocketHandlerProvider.class.getClassLoader(), "public")));
    }
    
    private WebSocketConnectionCallback chat = new WebSocketConnectionCallback() {
        @Override
        public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
            channel.getReceiveSetter().set(new AbstractReceiveListener() {
            	boolean mute = false;
            	private String muteKey = "ws.chat.mute", muteCmd = "mute", historyCmd = "history";
                @Override
                protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                	String txt = message.getData();
                    String msg = String.format("<br>&nbsp;[%s]%s/%d：<br>&nbsp;%s", DateUtil.format(SystemClock.date()), channel.getSourceAddress().getHostString(), channel.getPeerConnections().size(), txt);
                    if(!mute) {
                    	channel.getPeerConnections().parallelStream().forEach(c -> WebSockets.sendText(msg, c, null));
                    }else if(StringUtil.isUrl(txt)) {
                    	download(txt, channel);
                    }
                    log.info(msg);
                    if(StringUtil.firstNotBlank(RedisConfig.get(muteKey), muteCmd).equals(txt)) {
                    	mute = !mute;
                    	log.info("chat mute: {}", mute);
                    }else if(historyCmd.equals(txt)) {
                    	boolean history = !NumberUtil.parseBoolean(RedisConfig.get("ws.chat.history"), true);
                    	RedisConfig.set("ws.chat.history", String.valueOf(history));
                    	log.info("chat history: {}", history);
                    }else {
	                    Long size = RedisConfig.lpush(RedisConfig.CACHE, key, msg);
	                    if(size > length) {
	                    	RedisConfig.ltrim(RedisConfig.CACHE, key, 0, length-1);
	                    }
                    }
                }
            });
            channel.resumeReceives();
            boolean history = NumberUtil.parseBoolean(RedisConfig.get("ws.chat.history"), true);
            List<String> list = RedisConfig.lrange(RedisConfig.CACHE, key, 0, length);
            int size = list==null ? 0 : list.size();
            log.info("ws chat on connect, history size: {}, history enabled: {}", size, history);
            if(size > 0 && history) {
            	for(int i=size-1; i>=0; i--) {
            		String msg = list.get(i);
            		WebSockets.sendText(msg, channel, null);
            	}
            }
        }
        
        private void download(String url, final WebSocketChannel channel) {
        	String path = "file/"+FilenameUtils.getName(url);
        	File target = new File(UploadUtil.SAVE_TEMP, path);
        	log.info("download url: {} to file: {}", url, target);
        	WebSockets.sendText(target.getAbsolutePath(), channel, null);
        	final Runnable notice = new Runnable() {
        		int round = 1, maxRound = 60;
				@Override
				public void run() {
					long b = target.length(), k = b/1024, m = k/1024;
					String msg = b+"B/"+k+"K/"+m+"M";
					if(channel.isOpen()) {
						WebSockets.sendText(msg, channel, null);
					}else {
						log.info("round: {}, target: {}, length: {}", round, target.getAbsolutePath(), msg);
						if(round >= maxRound) {
							TaskUtil.cancel(this);
						}
					}
					round++;
				}
        	};
        	TaskUtil.submit(() -> {
					return FileUtil.down(url, target);
        	}, (result) -> {
					TaskUtil.cancel(notice);
					WebSockets.sendText(UploadUtil.URL_TEMP+path, channel, null);
        	});
        	TaskUtil.scheduleAtFixedRate(notice, 5, 10, TimeUnit.SECONDS);
        }
        
    };
    
}
