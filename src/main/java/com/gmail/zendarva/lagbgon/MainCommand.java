package com.gmail.zendarva.lagbgon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;

public class MainCommand extends CommandBase {

	private static ConfigManager config = ConfigManager.instance();
	private static long nextUnload;

	@Override
	public String getCommandName() {
		return "bgon";
	}

	@Override
	public String getCommandUsage(ICommandSender p_71518_1_) {
		return "/bgon : Shows help for using Lag'B'Gon";
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {

		EntityPlayer plr;
		ChatComponentText chat;

		if (sender instanceof EntityPlayer)
		{
			plr = (EntityPlayer) sender;
		}
		else
			return;


		switch (args.length)
		{
			case 0:
				chat = new ChatComponentText("/bgon toggleitem : toggles the whitelist status of held item");
				sender.addChatMessage(chat);
				chat = new ChatComponentText("/bgon toggleentity <name>: toggles the blacklist status of the named entity");
				sender.addChatMessage(chat);
				chat = new ChatComponentText("/bgon clear : Clears all items from the world not on whitelist and all entities on blacklist");
				sender.addChatMessage(chat);
				chat = new ChatComponentText("/bgon interval <minutes> : sets the interval for automatic running of /bgon clear" );
				sender.addChatMessage(chat);
				chat = new ChatComponentText("/bgon toggleauto : Toggles automatic clearing of entities.");
				sender.addChatMessage(chat);
				chat = new ChatComponentText("/bgon listitems : Lists the items in the whitelist.");
				sender.addChatMessage(chat);
				chat = new ChatComponentText("/bgon listentities : Lists the entities in the blacklist.");
				sender.addChatMessage(chat);
				chat = new ChatComponentText("/bgon scanentities : Lists nearby entities,by name, for blacklisting.");
				sender.addChatMessage(chat);

				break;
			case 1:
				if (args[0].equals("scanentities"))
				{
					scanEntities(plr);
				}

				if (args[0].equals("listitems"))
				{
					StringBuilder line = new StringBuilder();
					chat = new ChatComponentText("Item Blacklist contains:");
					sender.addChatMessage(chat);
					for (String item : ConfigManager.itemBlacklist)
					{
						if (line.length() > 40)
						{
							chat = new ChatComponentText(line.toString());
							sender.addChatMessage(chat);
							line = new StringBuilder();
						}
						line.append(item);
						line.append(", ");
					}
					if (line.length() > 0)
					{
						chat = new ChatComponentText((String) line.toString().subSequence(0, line.length()-2));
						sender.addChatMessage(chat);
					}
					return;
				}

				if (args[0].equals("listentities"))
				{
					StringBuilder line = new StringBuilder();
					chat = new ChatComponentText("Entity Blacklist contains:");
					sender.addChatMessage(chat);
					for (String item : ConfigManager.entityBlacklist)
					{
						if (line.length() > 40)
						{
							chat = new ChatComponentText(line.toString());
							sender.addChatMessage(chat);
							line = new StringBuilder();
						}
						line.append(item);
						line.append(", ");
					}
					if (line.length() > 0)
					{
						chat = new ChatComponentText((String) line.toString().subSequence(0, line.length()-2));
						sender.addChatMessage(chat);
					}
					return;
				}

				if (args[0].equals("toggleauto"))
				{
					config.toggleAuto();

					if (ConfigManager.automaticRemoval)
					{
						chat = new ChatComponentText("Automatic clearing enabled.");
						sender.addChatMessage(chat);
					}
					else
					{
						chat = new ChatComponentText("Automatic clearing disabled.");
						sender.addChatMessage(chat);
					}
					return;
				}
				if (args[0].equals("toggleitem"))
				{
					if (plr.getCurrentEquippedItem() == null)
					{
						chat = new ChatComponentText("You must have an item selected");
						sender.addChatMessage(chat);
						return;
					}

					Item item = plr.getCurrentEquippedItem().getItem();

					config.toggleItem(item);

					if (!config.isBlacklisted(plr.getCurrentEquippedItem().getItem()))
					{
						chat = new ChatComponentText(item.getItemStackDisplayName(plr.getCurrentEquippedItem()) + " removed from whitelist.");
						sender.addChatMessage(chat);
					}
					else
					{
						chat = new ChatComponentText(item.getItemStackDisplayName(plr.getCurrentEquippedItem()) + " added to whitelist.");
						sender.addChatMessage(chat);
					}

					return;
				}

				if (args[0].equals("clear"))
				{
					if (!DimensionManager.getWorld(0).isRemote)
						doClear();
					return;
				}

			case 2:

				if (args[0].equals("toggleentity"))
				{
					config.toggleEntity(args[1]);

					if (config.isBlacklisted(args[1]))
					{
						chat = new ChatComponentText(args[1] + " has been added to the blacklist.");
						sender.addChatMessage(chat);
					}
					else
					{
						chat = new ChatComponentText(args[1] + " has been removed from the blacklist.");
						sender.addChatMessage(chat);
					}
					return;
				}

				if (args[0].equals("interval"))
				{
					int newInterval = Integer.parseInt(args[1]);

					config.changeInterval(newInterval);
					chat = new ChatComponentText("Automatic removal interval set to: " + (newInterval + 1));
					sender.addChatMessage(chat);
				}


			default:
				if (args[0].equals("toggleentity"))
				{
					StringBuilder name = new StringBuilder();
					for (String word : args)
					{
						if (!word.equals("toggleentity"))
						{
							name.append(word);
							name.append(" ");
						}
					}
					name.replace(name.length()-1, name.length(), "");

					config.toggleEntity(name.toString());

					if (config.isBlacklisted(name.toString()))
					{
						chat = new ChatComponentText(name.toString() + " has been added to the blacklist.");
						sender.addChatMessage(chat);
					}
					else
					{
						chat = new ChatComponentText(name.toString() + " has been removed from the blacklist.");
						sender.addChatMessage(chat);
					}
					return;
				}
		}

	}

	public static void doClear()
	{
		EntityItem item;
		Entity entity;
		int itemsRemoved =0;
		int entitiesRemoved = 0;
		ArrayList<Object> toRemove = new ArrayList<Object>();
		for (World world : DimensionManager.getWorlds())
		{
			if (world == null)
				continue;
			if (world.isRemote)
			{
				System.out.println("How?!?");
			}
			//Seriously? I'm passing you to an object.  Who the hell cares?!?
			@SuppressWarnings("unchecked")
			Iterator<Object> iter = world.loadedEntityList.iterator();
			Object obj;
			while (iter.hasNext())
			{
				obj = iter.next();
				if (obj instanceof EntityItem)
				{
					item = (EntityItem) obj;
					if (!config.isBlacklisted(item.getEntityItem().getItem()))
					{
						toRemove.add(item);
						itemsRemoved++;
					}

				}
				else if (!(obj instanceof EntityPlayer))
				{
					entity = (Entity) obj;
					if (config.isBlacklisted(entity))
					{
						toRemove.add(entity);
						entitiesRemoved++;
					}
				}
			}
			for (Object o : toRemove)
			{
				((Entity)o).setDead();
			}
			toRemove.clear();
		}
		ChatComponentText chat = new ChatComponentText("Lag'B'Gon has removed " + itemsRemoved + " items and ");
		chat.appendText(entitiesRemoved + " entities!");
		MinecraftServer.getServer().getConfigurationManager().sendChatMsg(chat);
	}


	@Override
	public int getRequiredPermissionLevel() {

		return 2;
	}



	private static long mean(long num[])
	{
		long val = 0;
		for (long n : num)
		{
			val+=n;
		}
		return val/num.length;
	}
	private void scanEntities(EntityPlayer plr)
	{
		AxisAlignedBB bb;
		Entity ent;
		ChatComponentText chat;
		bb = AxisAlignedBB.getBoundingBox(plr.posX -5, plr.posY-5, plr.posZ-5, plr.posX+5,plr.posY+5,plr.posZ+5);
		@SuppressWarnings("unchecked")
		List<Object> Entities = plr.worldObj.getEntitiesWithinAABB(Entity.class, bb);
		ArrayList<String> entityNames = new ArrayList<String>();
		for (Object obj : Entities)
		{
			ent = (Entity) obj;
			if (!entityNames.contains(ent.getCommandSenderName()))
			{
				entityNames.add(ent.getCommandSenderName());
			}

		}

		StringBuilder line = new StringBuilder();
		chat = new ChatComponentText("Nearby Entities");
		plr.addChatMessage(chat);
		for (String item : entityNames)
		{
			if (line.length() > 40)
			{
				chat = new ChatComponentText(line.toString());
				plr.addChatMessage(chat);
				line = new StringBuilder();
			}
			line.append(item);
			line.append(", ");
		}
		if (line.length() > 0)
		{
			chat = new ChatComponentText((String) line.toString().subSequence(0, line.length()-2));
			plr.addChatMessage(chat);
		}
	}

}
	
