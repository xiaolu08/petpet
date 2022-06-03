package xmmt.dituon.plugin;

import net.mamoe.mirai.Bot;
import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.Member;
import net.mamoe.mirai.contact.MemberPermission;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.events.BotOnlineEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.NudgeEvent;
import net.mamoe.mirai.message.data.*;

import java.util.ArrayList;

public final class Petpet extends JavaPlugin {
    public static final Petpet INSTANCE = new Petpet();

    Bot bot = null;
    ArrayList<Group> disabledGroup = new ArrayList<>();
    PluginPetService pluginPetService;

    private Petpet() {
        super(new JvmPluginDescriptionBuilder("xmmt.dituon.petpet", "2.3")
                .name("PetPet")
                .author("Dituon")
                .build());
        this.pluginPetService = new PluginPetService();
    }

    @Override
    public void onEnable() {
        this.reloadPluginConfig(PetPetAutoSaveConfig.INSTANCE);

        pluginPetService.readConfigByPluginAutoSave();
        pluginPetService.readData(getDataFolder());

        GlobalEventChannel.INSTANCE.subscribeAlways(BotOnlineEvent.class, e -> {
            if (bot == null) {
                bot = e.getBot();
                bot.getEventChannel().subscribeAlways(GroupMessageEvent.class, this::onGroupMessage);
                bot.getEventChannel().subscribeAlways(NudgeEvent.class, this::onNudge);
            }
        });
    }

    private void onNudge(NudgeEvent e) {
        if (isDisabled((Group) e.getSubject())) {
            return; // 如果禁用了petpet就返回
        }
        try {
            pluginPetService.sendImage((Group) e.getSubject(), (Member) e.getFrom(), (Member) e.getTarget(), true);
        } catch (Exception ex) { // 如果无法把被戳的对象转换为Member(只有Bot无法强制转换为Member对象)
            pluginPetService.sendImage((Group) e.getSubject(), (Member) e.getFrom(), ((Group) e.getSubject()).getBotAsMember(), true);
        }
    }

    private void onGroupMessage(GroupMessageEvent e) {
        if (e.getMessage().contentToString().equals((pluginPetService.command + " off")) && !isDisabled(e.getGroup()) && isPermission(e)) {
            disabledGroup.add(e.getGroup());
            sendReplyMessage(e, "已禁用 " + pluginPetService.command);
            return;
        }

        if (e.getMessage().contentToString().equals((pluginPetService.command + " on")) && isDisabled(e.getGroup()) && isPermission(e)) {
            disabledGroup.remove(e.getGroup());
            sendReplyMessage(e, "已启用 " + pluginPetService.command);
            return;
        }

        if (pluginPetService.keyCommand && e.getMessage().contains(At.Key)) {
            String key = null;
            for (Message m : e.getMessage()) {
                if (m instanceof PlainText &&
                        pluginPetService.dataMap.containsKey(m.contentToString().replace(" ", ""))) {
                    key = m.contentToString().replace(" ", "");
                    continue;
                }
                if (m instanceof At && key != null) {
                    At at = (At) m;
                    pluginPetService.sendImage(e.getGroup(), e.getSender(), e.getGroup().get(at.getTarget()), key);
                    return;
                }
            }
        }

        if (!isDisabled(e.getGroup()) && e.getMessage().contentToString().startsWith(pluginPetService.command)) {
            if (pluginPetService.respondImage && e.getMessage().contains(Image.Key)) {
                getLogger().info("img");
                String toURL = null;
                for (Message m : e.getMessage()) {
                    if (m instanceof Image) {
                        toURL = Image.queryUrl((Image) m);
                        getLogger().info(toURL);
                        continue;
                    }
                    if (m instanceof PlainText && toURL != null && !m.contentToString().endsWith(" ")) {
                        pluginPetService.sendImage(e.getGroup(), e.getSender(), e.getSender().getAvatarUrl(), toURL,
                                m.contentToString().replace(" ", ""), new String[]{
                                        e.getSender().getNameCard().isEmpty()? e.getSender().getNick(): e.getSender().getNameCard(),
                                        "你",
                                        e.getGroup().getName()
                                });
                        return;
                    }
                }
                return;
            }
                At at = null;
                Member to = e.getSender();
                for (Message m : e.getMessage()) {
                    if (m instanceof At) { // 遍历消息取出At的对象
                        at = (At) m;
                        to = e.getGroup().get(at.getTarget());
                        continue;
                    }
                    if (m instanceof PlainText && at != null && !m.contentToString().endsWith(" ")) {
                        pluginPetService.sendImage(e.getGroup(), e.getSender(), to, m.contentToString().replace(" ", ""));
                        return;
                    }
                }
                pluginPetService.sendImage(e.getGroup(), e.getSender(), to);
        }
    }

    private boolean isDisabled(Group group) {
        if (disabledGroup != null && !disabledGroup.isEmpty()) {
            return disabledGroup.contains(group);
        }
        return false;
    }

    public boolean isPermission(GroupMessageEvent e) {
        return e.getPermission() == MemberPermission.ADMINISTRATOR || e.getPermission() == MemberPermission.OWNER;
    }

    private void sendReplyMessage(GroupMessageEvent e, String text) {
        e.getGroup().sendMessage(new QuoteReply(e.getMessage()).plus(text));
    }
}