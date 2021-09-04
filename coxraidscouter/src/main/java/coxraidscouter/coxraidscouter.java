package coxraidscouter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.events.*;
import net.runelite.api.queries.DecorativeObjectQuery;
import net.runelite.api.queries.GameObjectQuery;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.iutils.*;
import net.runelite.api.MenuAction;
import net.runelite.client.util.Text;
import org.pf4j.Extension;
import javax.inject.Inject;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
	name = "CoxRaidScouter",
	description = "Requires Chambers Xeric Plugin post raids to chatbox toggle to be on. Also depends upon illumines iUtils",
	tags = {"cox", "raid", "scouter"}
)
@Slf4j
public class coxraidscouter extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private iUtils utils;

	@Inject
	private MenuUtils menu;

	@Inject
	private MouseUtils mouse;

	@Inject
	private coxraidscouterconfig config;

	@Provides
	coxraidscouterconfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(coxraidscouterconfig.class);
	}

	private int timeout = 0;
	private String state = "read";
	private String raidLeaverState = "Awaiting Raider";
	private boolean raidFound = false;
	private boolean raidStarted = false;
	private String raidLayout = null;

	private int SleepDelay(int min, int max){
		Random random = new Random();
		return random.ints(min,(max+1)).findFirst().getAsInt();
	}

	private void reset()
	{
		timeout=0;
		state ="read";
		raidLeaverState ="Awaiting Raider";
		raidFound = false;
		raidLayout = null;
		raidStarted = false;
	}

	private Point getLocation(TileObject tileObject)
	{
		if (tileObject instanceof GameObject)
		{

			return ((GameObject) tileObject).getSceneMinLocation();
		}
		else
		{
			return new Point(tileObject.getLocalLocation().getSceneX(), tileObject.getLocalLocation().getSceneY());
		}
	}

	private void pressKey()
	{
		KeyEvent keyPress = new KeyEvent(this.client.getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER);
		this.client.getCanvas().dispatchEvent(keyPress);
		KeyEvent keyRelease = new KeyEvent(this.client.getCanvas(), KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER);
		this.client.getCanvas().dispatchEvent(keyRelease);
		KeyEvent keyTyped = new KeyEvent(this.client.getCanvas(), KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER);
		this.client.getCanvas().dispatchEvent(keyTyped);
	}

	private void Print(String string) //used for debugging, puts a message to the in game chat.
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE,"",string,"");
	}

	private void StateHandler() throws IOException {
		if (timeout!=0)
		{
			return;
		}

		switch (state)
		{
			case "read":
				DecorativeObject recruitingBoardObject = new DecorativeObjectQuery()
						.idEquals(ObjectID.RECRUITING_BOARD)
						.result(client)
						.nearestTo(client.getLocalPlayer());
				if (recruitingBoardObject != null) {
					MenuEntry ReadBoard = new MenuEntry("Read", "<col=ffff>Recruiting board", recruitingBoardObject.getId(), MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), getLocation(recruitingBoardObject).getX(), getLocation(recruitingBoardObject).getY(), false);
					menu.setEntry(ReadBoard);
					mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(2000, 3000));
					state = "check for existing raid";
					timeout += 8;//tick delay between actions
				}
				break;

			case "check for existing raid":
				if (client.getWidget(229,1)!=null) //if raid occupied message shows
				{
					if (client.getWidget(229,1).getText().startsWith("Your party has embarked on its raid"))
					{
						if (raidStarted)
						{
							reset(); //restarts the scouter when the raid is started
						}
					}
				}

				if (client.getWidget(499, 2) != null) //if board is visible scouting can be immediately started
				{
					state = "make party";
					raidFound = false;
				}
				timeout +=1;
				break;

			case "make party":
				if (client.getWidget(499, 58) != null) {
					MenuEntry MakeParty = new MenuEntry("Make party", "", 1, MenuAction.CC_OP.getId(), -1, 32702522, false);
					menu.setEntry(MakeParty);
					mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(300, 800));
					state = "enter";
					timeout += 4;
				}
				break;
			case "enter":
				GameObject EnterRaidObject = new GameObjectQuery()
						.idEquals(ObjectID.CHAMBERS_OF_XERIC)
						.result(client)
						.nearestTo(client.getLocalPlayer());
				if (EnterRaidObject != null) {
					MenuEntry EnterCox = new MenuEntry("Enter", "<col=ffff>Chambers of Xeric", EnterRaidObject.getId(), MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), getLocation(EnterRaidObject).getX(), getLocation(EnterRaidObject).getY(), false);
					menu.setEntry(EnterCox);
					mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(300, 800));
					state = "start raid";
					timeout += 16;
				}
				break;
			case "start raid": //main logic for leaving raid, either starts and continues scouting or waits til raider joins then leaves and rejoins cc

				if (config.fivehHandler()) //clicks through the 5h check at raid entrance if visible.
				{
					if (client.getWidget(229,1) != null)
					{
						if (client.getWidget(229, 1).getText().startsWith("You have been logged in for a very long time"))
						{
							MenuEntry firstMenu = new MenuEntry("Continue", "", 0, MenuAction.WIDGET_TYPE_6.getId(), -1, 15007746, false);
							menu.setEntry(firstMenu);
							mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(300, 800));
							timeout += 4;
							return;
						}
					}

					if (client.getWidget(219,1) != null)
					{
						if (client.getWidget(219, 1).getChild(1).getText().startsWith("Yes, and don't ask again in this session"))
						{
							MenuEntry secondMenu = new MenuEntry("Continue", "", 0, MenuAction.WIDGET_TYPE_6.getId(), 1, 14352385, false);
							menu.setEntry(secondMenu);
							mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(300, 800));
							timeout += 4;
							return;
						}
					}
				}

				if (raidFound) {
					if (config.autoLeaveCC()) {
						switch (raidLeaverState) {
							case "Awaiting Raider":
								if (client.getWidget(707, 3) != null) {
									MenuEntry openClanTab = new MenuEntry("Chat-channel", "", 1, MenuAction.CC_OP.getId(), -1, 46333955, false);
									menu.setEntry(openClanTab);
									mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(300, 500));
									timeout += 2;
									raidLeaverState = "Leaving CC";
								}
								break;

							case "Leaving CC":
								if (client.getWidget(7, 18) != null) {
									if (client.getPlayers().size() > 1) {
										if (!config.webhook().equals("")) //if webhook exists, post to webhook that raid is taken
										{
											DiscordWebhook webhook = new DiscordWebhook(config.webhook());
											webhook.setContent(client.getLocalPlayer().getName() + " - Raid Taken. Now Scouting.");
											webhook.execute();
										}
										Print("Raider in raid, should be leaving cc");
										MenuEntry LeaveCC = new MenuEntry("Leave", "", 6, MenuAction.CC_OP_LOW_PRIORITY.getId(), -1, 458770, true);
										menu.setEntry(LeaveCC);
										mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(100, 300));
										timeout += 4;
										raidLeaverState = "ClickToContinue";
									}
								}
								break;

							case "ClickToContinue":
								recruitingBoardObject = new DecorativeObjectQuery()
										.idEquals(ObjectID.RECRUITING_BOARD)
										.result(client)
										.nearestTo(client.getLocalPlayer());
								if (recruitingBoardObject != null) {
									MenuEntry ClickToContinue = new MenuEntry("Continue", "", 0, MenuAction.WIDGET_TYPE_6.getId(), -1, 15007746, false);
									menu.setEntry(ClickToContinue);
									mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(300, 800));
									timeout += 3;
									raidLeaverState = "RejoinCC";
								}
								break;

							case "RejoinCC":
								if (client.getWidget(7, 18) != null) {
									MenuEntry JoinCC = new MenuEntry("Join", "", 1, MenuAction.CC_OP.getId(), -1, 458770, false);
									menu.setEntry(JoinCC);
									mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(300, 800));
									timeout += 3;
									raidLeaverState = "PressEnter";
								}
								break;

							case "PressEnter":
								if (client.getWidget(162, 37) != null) {
									Executors.newSingleThreadExecutor() //runs pressKey function to press enter once
											.submit(this::pressKey);
									raidLeaverState = "Idle";
									timeout += 3;
								}
								break;
							case "Idle":
								reset();
								break;
						}
					} else {
						timeout += 36000; //if raid found and auto/rejoin is disabled then wait for 6h
					}
				} else if (client.getWidget(500, 12) != null) {
					MenuEntry StartRaid = new MenuEntry("Start raid", "", 1, MenuAction.CC_OP.getId(), -1, 32768014, false);
					menu.setEntry(StartRaid);
					mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(300, 800));
					state = "continue";
					timeout += 4;
				}
				break;
			case "continue":
				if (client.getWidget(219, 1) != null) {
					MenuEntry BeginRaid = new MenuEntry("Continue", "", 0, MenuAction.WIDGET_TYPE_6.getId(), 1, 14352385, false);
					menu.setEntry(BeginRaid);
					mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(300, 800));
					state = "climb";
					timeout += 4;
				}
				break;
			case "climb":
				GameObject ClimbStepsObject = new GameObjectQuery()
						.idEquals(ObjectID.STEPS_29778)
						.result(client)
						.nearestTo(client.getLocalPlayer());
				if (ClimbStepsObject != null) {
					MenuEntry ClimbSteps = new MenuEntry("Climb", "<col=ffff>Steps", ClimbStepsObject.getId(), MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), getLocation(ClimbStepsObject).getX(), getLocation(ClimbStepsObject).getY(), false);
					menu.setEntry(ClimbSteps);
					mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(300, 800));
					state = "leave raid";
					timeout += 4;
				}
				break;
			case "leave raid":
				if (client.getWidget(219, 1) != null) {
					MenuEntry LeaveRaid = new MenuEntry("Continue", "", 0, MenuAction.WIDGET_TYPE_6.getId(), 1, 14352385, false);
					menu.setEntry(LeaveRaid);
					mouse.delayMouseClick(client.getMouseCanvasPosition(), SleepDelay(300, 800));
					state = "read";
				}
				break;
		}
	}


	@Override
	protected void shutDown()
	{
		reset();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) throws IOException {
		if (client.getGameState() == GameState.LOGIN_SCREEN)
		{
			if (!config.webhook().equals("")) //if webhook exists, post to webhook that scouter has logged out
			{
				DiscordWebhook webhook = new DiscordWebhook(config.webhook());
				webhook.setContent(client.getLocalPlayer().getName() + " - Logged out");
				webhook.execute();
				reset();
			}
		}
	}

	@Subscribe
	private void onGameTick(GameTick event) throws IOException {
		if (timeout!=0)
		{
			timeout--;
		}
		StateHandler();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) throws IOException {
		if ((Text.removeTags((event.getMessage())).endsWith("start the raid without you. Sorry."))) //Restarts the plugin if someone starts the raid currently held
		{
			raidStarted = true;
		}
		if ((Text.removeTags(event.getMessage())).startsWith("Layout"))
		{
			//kinda clunky code, can definitely be written better
			//Splits Chatmessage into list rooms and strips whitespace, also pulls raid layout
			String[] rooms = Text.removeTags(event.getMessage()).split(":")[2].split(",");
			raidLayout = Text.removeTags(event.getMessage()).split(":")[1];
			List<String> roomsTrimmed = new ArrayList<>();
			//loop through arraylist and remove whitespace
			for (String room: rooms)
			{
				roomsTrimmed.add(room.trim());
			}

			if (roomsTrimmed.size()!=5) //returns if not 3c2p (or 4c1p but these are unscoutable)
			{
				if (config.debugScouting()) //if debugScouting enabled adds reason for leaving raid to game chat
				{
					Print("Raid is incorrect size");
				}
				return;
			}

			if (config.desiredRotationsToggle()) //if desiredRotations enabled then only allows specific rotations through
			{
				boolean rotationMatchFound = false;
				//a lot of bodged additions to make the string fit with the Match
				String compareRotation  = roomsTrimmed
						.toString()
						.replace("[","")
						.replace("]","")
						.replace("Tightrope","")
						.replace("Crabs","")
						.replace("Ice Demon","")
						.replace("Thieving","")
						.replaceAll(",+","")
						.replace("  "," ")
						.strip();
				List<String> allMatches = new ArrayList<String>();
				Matcher m = Pattern.compile("\\[(.*?)]")
						.matcher(config.desiredRotations());
				while (m.find()) {
					allMatches.add(m.group());
				}
				for (String item : allMatches)
				{
					item = item
							.replace(","," ")
							.replace("[","")
							.replace("]","")
							.strip();
					if (item.equals(compareRotation))
					{
						rotationMatchFound = true;
					}
				}
				if (!rotationMatchFound)
				{
					if (config.debugScouting()) //if debugScouting enabled adds reason for leaving raid to game chat
					{
						Print("No rotation match found");
					}
					return;
				}
			}

			if (config.requireRope()) //checks to see if Tightrope is in raid
			{
				if (!(roomsTrimmed.contains("Tightrope")))
				{
					if (config.debugScouting()) //if debugScouting enabled adds reason for leaving raid to game chat
					{
						Print("Raid doesn't contain Tightrope");
					}
					return; //returns if no tightrope
				}
			}

			if (config.requireGoodCrabs()) //checks to see if Crabs are good if they exist within raid
			{
				if(roomsTrimmed.contains("Crabs")) //If crabs exist in raid rotation
				{
					if (!(raidLayout.strip().startsWith("[SCS"))) //SCS is always good crabs so break out of if statement if it starts with SCS
					{
						if (!roomsTrimmed.get(4).equals("Crabs")) //Crabs 5th is always good crabs so return if it's not crabs
						{
							if (config.debugScouting()) //if debugScouting enabled adds reason for leaving raid to game chat
							{
								Print("Raid contains bad crabs");
							}
							return; //returns if bad crabs
						}
					}
				}
			}


			for (String room: roomsTrimmed) //checks through blacklisted rooms
			{
				String[] blacklistedRooms = config.blacklistedRooms().split(",");
				for (String blacklistedRoom: blacklistedRooms)
				{
					if (room.equals(blacklistedRoom))
					{
						if (config.debugScouting()) //if debugScouting enabled adds reason for leaving raid to game chat
						{
							Print("Blacklisted room found: " + room);
						}
						return; //returns if any blacklisted room in raid
					}
				}
			}


			for (String room: roomsTrimmed) //checks for Mutt/Tek(Ovl)
			{
				String[] ovlRooms = {"Tekton", "Muttadiles"};
				for (String ovlRoom: ovlRooms )
				{
					if (room.equals(ovlRoom))
					{
						Print("Raid Found, waiting for raider.");
						raidFound=true;
						if (!config.webhook().equals("")) //if webhook exists, post raid, world, cc to webhook
						{
							DiscordWebhook webhook = new DiscordWebhook(config.webhook());
							webhook.setTts(true);
							webhook.addEmbed(new DiscordWebhook.EmbedObject()
									.setTitle(client.getLocalPlayer().getName())
									.setDescription(roomsTrimmed.toString())
									.setColor(Color.GREEN)
									.addField("World", String.valueOf(client.getWorld()), true)
									.addField("Layout", raidLayout, true));
							webhook.execute(); //Handle exception
						}
						return; //returns if mutt or tek found, returns to prevent double printing Chat Message
					}
				}
			}
			if (config.debugScouting()) //if debugScouting enabled adds reason for leaving raid to game chat
			{
				Print("No Overload in raid");
			}
		}
	}
}