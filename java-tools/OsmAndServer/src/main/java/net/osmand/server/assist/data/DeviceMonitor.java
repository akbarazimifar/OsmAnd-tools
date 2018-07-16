package net.osmand.server.assist.data;

import java.io.Serializable;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import net.osmand.server.assist.DeviceLocationManager;
import net.osmand.server.assist.OsmAndAssistantBot;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.updateshandlers.SentCallback;

import com.google.gson.JsonObject;

public class DeviceMonitor {
	private static final Log LOG = LogFactory.getLog(DeviceLocationManager.class);
	ConcurrentHashMap<Long, LocationChatMessage> chats = new ConcurrentHashMap<>();
	final OsmAndAssistantBot bot;
	final Device device;
	// signal with last location 
	LocationInfo lastSignal = new LocationInfo();
	// signal with last location 
	LocationInfo lastLocationSignal = null;

	public DeviceMonitor(Device device, OsmAndAssistantBot bot) {
		this.device = device;
		this.bot = bot;
	}
	
	public void sendLocation(LocationInfo info) {
		LocationInfo locSignal = lastLocationSignal;
		if(info.isLocationPresent()) {
			locSignal = info;
		}
		sendLocation(info, locSignal);
	}

	public void sendLocation(LocationInfo info, LocationInfo locSignal) {
		lastSignal = info;
		lastLocationSignal = locSignal;
		for (LocationChatMessage lm : chats.values()) {
			if (lm.isEnabled()) {
				lm.sendMessage(bot, device, info, locSignal);
			}
		}
	}
	
	public void startMonitoring(Long chatId) {
		LocationChatMessage lm = getOrCreateLocationChat(chatId);
		lm.enable();
		
	}
	
	public void showLiveMessage(Long chatId) {
		LocationChatMessage lm = getOrCreateLocationChat(chatId);
		lm.sendMessage(bot, device, lastSignal, lastLocationSignal);
	}
	
	public void stopMonitoring(Long chatId) {
		LocationChatMessage lm = getLocationChat(chatId);
		if (lm != null) {
			lm.disable();
		}
	}
	
	public LocationChatMessage getOrCreateLocationChat(Long chatId) {
		LocationChatMessage lm = chats.get(chatId);
		if(lm == null) {
			lm = new LocationChatMessage(this, chatId);
			chats.put(chatId, lm);
		}
		return lm;
	}
	
	public LocationChatMessage getLocationChat(Long chatId) {
		return chats.get(chatId);
	}
	
	public Device getDevice() {
		return device;
	}
	
	public LocationInfo getLastSignal() {
		return lastSignal;
	}
	
	public LocationInfo getLastLocationSignal() {
		return lastLocationSignal;
	}
	
	public boolean isLocationMonitored(Long chatId) {
		LocationChatMessage lm = chats.get(chatId);
		if(lm != null) {
			return lm.isEnabled();
		}
		return false;
	}
	
	public boolean isOneChatMonitored() {
		for(LocationChatMessage m : chats.values()) {
			if(m.isEnabled()) {
				return true; 
			}
		}
		return false;
	}
	

	public static class LocationChatMessage {
		private boolean JSON_MESSAGE = false;
		
		final long chatId;
		final DeviceMonitor mon;
		

		int messageId;
		long initialTimestamp = System.currentTimeMillis();
		long updateTime;
		boolean isLiveLocation;

		int updateId = 1;
		private boolean enabled;
		private long disabledTimestamp;

		public LocationChatMessage(DeviceMonitor d, Long chatId) {
			mon = d;
			this.chatId = chatId;
		}

		public void enable() {
			this.enabled = true;
		}

		public boolean isEnabled() {
			return enabled;
		}
		
		public long getDisabledTimestamp() {
			return disabledTimestamp;
		}

		public void disable() {
			disabledTimestamp = System.currentTimeMillis();
			enabled = false;
		}

		public int deleteOldMessage() {
			int oldMessageId = messageId;
			if(messageId != 0) {
				mon.bot.sendMethodAsync(new DeleteMessage(chatId, messageId), new SentCallback<Boolean>() {
					@Override
					public void onResult(BotApiMethod<Boolean> method, Boolean response) {
						
					}

					@Override
					public void onException(BotApiMethod<Boolean> method, Exception exception) {
						LOG.error(exception.getMessage(), exception);
					}

					@Override
					public void onError(BotApiMethod<Boolean> method, TelegramApiRequestException apiException) {
						LOG.error(apiException.getMessage(), apiException);
					}
				});
				messageId = 0;
			}
			return oldMessageId;
		}
		
		public String getMessageTxt(OsmAndAssistantBot bot, Device device, 
				LocationInfo lastSignal, LocationInfo lastSig) {
			
			StringBuilder bld = new StringBuilder();
			String locMsg = bot.formatLocation(lastSignal);
			bld.append(String.format("<b>Device</b>: %s\n<b>Location</b>: %s\n", device.deviceName, locMsg));
			if(!lastSignal.isLocationPresent() && lastSig != null && lastSig.isLocationPresent()) {
				bld.append(String.format("<b>Last location</b>: %s\n", bot.formatLocation(lastSig)));
			}
			boolean locationCurrentlyPresent = lastSignal.isLocationPresent();
			if (!Double.isNaN(lastSignal.altitude) && lastSignal.isLocationPresent()) {
				bld.append(String.format("<b>Altitude</b>: %.1f\n", (float) lastSignal.altitude));
			}
			if (!Double.isNaN(lastSignal.azi) && locationCurrentlyPresent) {
				bld.append(String.format("<b>Altitude</b>: %.1f\n", (float) lastSignal.altitude));
			}
			if (!Double.isNaN(lastSignal.speed) && locationCurrentlyPresent) {
				bld.append(String.format("<b>Speed</b>: %.1f\n", lastSignal.speed));
			}
			if (!Double.isNaN(lastSignal.satellites) && locationCurrentlyPresent) {
				bld.append(String.format("<b>Sattelites</b>: %d\n", (int) (int) lastSignal.satellites));
			}
			if (!Double.isNaN(lastSignal.hdop) && locationCurrentlyPresent) {
				bld.append(String.format("<b>Horizontal precision</b>: %d\n", (int) lastSignal.hdop));
			}
			if (!Double.isNaN(lastSignal.temperature)) {
				bld.append(String.format("<b>Temperature</b>: %.1f\n", lastSignal.temperature));
			}
			bld.append(String.format("Updated: %s (%d)\n", bot.formatFullTime(lastSignal.getTimestamp()) ,updateId++));
			return bld.toString().trim();
		
		}
		public String sendMessage(OsmAndAssistantBot bot, Device device, 
				LocationInfo lastSignal, LocationInfo lastLocationSignal) {
			updateTime = System.currentTimeMillis();
			String txt = JSON_MESSAGE ? getMessageJson(bot, device, lastSignal, lastLocationSignal).toString() : 
				getMessageTxt(bot, device, lastSignal, lastLocationSignal);
			sendMsg(bot, device, txt);
			return txt;
		}
		
		public JsonObject getMessageJson(OsmAndAssistantBot bot, Device device, 
				LocationInfo lastSignal, LocationInfo lastLocationSignal) {
			JsonObject obj = new JsonObject();
			updateTime = System.currentTimeMillis();
			obj.addProperty("name", device.deviceName);
			obj.addProperty("id", device.getEncodedId());
			boolean locationCurrentlyPresent = lastSignal.isLocationPresent();
			// display location from lastSignal anyway though it could be deprecated
			if (locationCurrentlyPresent) {
				obj.addProperty("lat", (float) lastSignal.lat);
				obj.addProperty("lon", (float) lastSignal.lon);
			} else if (lastLocationSignal != null && lastLocationSignal.isLocationPresent()) {
				obj.addProperty("lat", (float) lastLocationSignal.lat);
				obj.addProperty("lon", (float) lastLocationSignal.lon);
			}
			if (!Double.isNaN(lastSignal.altitude) && locationCurrentlyPresent) {
				obj.addProperty("alt", (float) lastSignal.altitude);
			}
			
			if (!Double.isNaN(lastSignal.azi) && locationCurrentlyPresent) {
				obj.addProperty("azi", (float) lastSignal.azi);
			}
			if (!Double.isNaN(lastSignal.speed) && locationCurrentlyPresent) {
				obj.addProperty("spd", (float) lastSignal.speed);
			}
			if (!Double.isNaN(lastSignal.satellites) && locationCurrentlyPresent) {
				obj.addProperty("sat", (int) lastSignal.satellites);
			}
			if (!Double.isNaN(lastSignal.hdop) && locationCurrentlyPresent) {
				obj.addProperty("hdop", (int) lastSignal.hdop);
			}
			if (!Double.isNaN(lastSignal.temperature)) {
				obj.addProperty("temp", (float) lastSignal.temperature);
			}
			if (lastSignal.isLocationPresent()) {
				obj.addProperty("locAgo", (Long) ((updateTime - lastSignal.timestamp)) / 1000);
			} else if (lastLocationSignal != null) {
				obj.addProperty("locAgo", (Long) ((updateTime - lastLocationSignal.timestamp)) / 1000);
			}
			long signalAgo = (Long) ((updateTime - lastSignal.timestamp)) / 1000;
			if(signalAgo > 5) {
				obj.addProperty("signalAgo", (Long) ((updateTime - lastSignal.timestamp)) / 1000);
			}
			// if(dd.timeLastValid != 0) {
			// obj.addProperty("locAgo", (Long)(time/ 1000 - dd.timeLastValid));
			// }
			obj.addProperty("updId", updateId++);
			obj.addProperty("updTime", (Long) ((updateTime - initialTimestamp) / 1000));
			return obj;
		}

		private void sendMsg(OsmAndAssistantBot bot, Device device, String txt) {
			InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
			markup.getKeyboard().add(Collections.singletonList(new InlineKeyboardButton("Hide").setCallbackData("dv|"+
					device.getEncodedId() + "|hide")));
			if (messageId == 0) {
				bot.sendMethodAsync(new SendMessage(chatId, txt).setReplyMarkup(markup).enableHtml(true), new SentCallback<Message>() {

					@Override
					public void onResult(BotApiMethod<Message> method, Message response) {
						messageId = response.getMessageId();
					}

					@Override
					public void onException(BotApiMethod<Message> method, Exception exception) {
						LOG.error(exception.getMessage(), exception);
					}

					@Override
					public void onError(BotApiMethod<Message> method, TelegramApiRequestException apiException) {
						LOG.error(apiException.getMessage(), apiException);
					}
				});
			} else {
				EditMessageText mtd = new EditMessageText();
				mtd.setChatId(chatId);
				mtd.setMessageId(messageId);
				mtd.setText(txt);
				mtd.enableHtml(true);
				mtd.setReplyMarkup(markup);
				bot.sendMethodAsync(mtd, new SentCallback<Serializable>() {
					@Override
					public void onResult(BotApiMethod<Serializable> method, Serializable response) {
					}

					@Override
					public void onException(BotApiMethod<Serializable> method, Exception exception) {
						LOG.error(exception.getMessage(), exception);
					}

					@Override
					public void onError(BotApiMethod<Serializable> method, TelegramApiRequestException apiException) {
						LOG.error(apiException.getMessage(), apiException);
					}
				});
			}
		}
	}


	

	
}