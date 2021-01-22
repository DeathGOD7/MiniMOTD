/*
 * This file is part of MiniMOTD, licensed under the MIT License.
 *
 * Copyright (c) 2021 Jason Penilla
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package xyz.jpenilla.minimotd.fabric;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.commands.Commands;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.minimotd.common.UpdateChecker;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public class MiniMOTDFabric implements ModInitializer {

  private static MiniMOTDFabric instance = null;

  private final Logger logger = LogManager.getLogger("MiniMOTD");
  private final MiniMessage miniMessage = MiniMessage.get();
  private final GsonComponentSerializer gsonComponentSerializer = GsonComponentSerializer.gson();
  private final GsonComponentSerializer downsamplingGsonComponentSerializer = GsonComponentSerializer.colorDownsamplingGson();
  private final MiniMOTD miniMOTD = new MiniMOTD(new File("").getAbsoluteFile().toPath().resolve("config/MiniMOTD"));

  private FabricServerAudiences audiences;
  private int protocolVersionCache;

  public MiniMOTDFabric() {
    if (instance != null) {
      throw new IllegalStateException("Cannot create a second instance of " + this.getClass().getName());
    }
    instance = this;
  }

  public int protocolVersionCache() {
    return this.protocolVersionCache;
  }

  public void protocolVersionCache(final int protocolVersionCache) {
    this.protocolVersionCache = protocolVersionCache;
  }

  public @NonNull MiniMOTD miniMOTD() {
    return this.miniMOTD;
  }

  public @NonNull MiniMessage miniMessage() {
    return this.miniMessage;
  }

  public @NonNull GsonComponentSerializer downsamplingGsonComponentSerializer() {
    return this.downsamplingGsonComponentSerializer;
  }

  public @NonNull GsonComponentSerializer gsonComponentSerializer() {
    return this.gsonComponentSerializer;
  }

  public @NonNull FabricServerAudiences audiences() {
    return this.audiences;
  }

  @Override
  public void onInitialize() {
    this.registerCommand();
    ServerLifecycleEvents.SERVER_STARTED.register(minecraftServer -> {
      this.audiences = FabricServerAudiences.of(minecraftServer);
      if (this.miniMOTD.configManager().pluginSettings().updateChecker()) {
        CompletableFuture.runAsync(() -> new UpdateChecker("{version}").checkVersion().forEach(this.logger::info));
      }
    });
    this.logger.info("Done initializing MiniMOTD");
  }

  private void registerCommand() {
    CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> dispatcher.register(
      Commands.literal("minimotd")
        .requires(source -> source.hasPermission(4))
        .then(Commands.literal("reload")
          .executes(ctx -> {
            final Audience audience = this.audiences.audience(ctx.getSource());
            this.send(audience, "<white>[<gradient:blue:aqua>MiniMOTD</gradient>] <italic><gray>Reloading MiniMOTD...");
            this.miniMOTD.configManager().loadConfigs();
            this.miniMOTD.iconManager().loadIcons();
            this.send(audience, "<white>[<gradient:blue:aqua>MiniMOTD</gradient>] <green>Done reloading MiniMOTD.");
            return 1;
          })
        )
        .then(Commands.literal("about")
          .executes(ctx -> {
            this.send(this.audiences.audience(ctx.getSource()),
              "<strikethrough><gradient:black:white>------------------",
              "<hover:show_text:'<gradient:blue:aqua>click me!'><click:open_url:https://github.com/jmanpenilla/MiniMOTD-Fabric>    MiniMOTD-Fabric",
              "<gray>      By <gradient:gold:yellow>jmp",
              "<strikethrough><gradient:black:white>------------------"
            );
            return 1;
          })
        )
    ));
  }

  private void send(final @NonNull Audience audience, final String... strings) {
    for (final String string : strings) {
      audience.sendMessage(this.miniMessage.parse(string));
    }
  }

  public static @NonNull MiniMOTDFabric get() {
    return instance;
  }

  public static final class MiniMOTD extends xyz.jpenilla.minimotd.common.MiniMOTD<String> {

    public MiniMOTD(final @NonNull Path dataDirectory) {
      super(
        dataDirectory,
        LoggerFactory.getLogger(MiniMOTD.class),
        MiniMOTD::loadIcon
      );
    }

    private static @NonNull String loadIcon(final @NonNull BufferedImage bufferedImage) throws Exception {
      Validate.validState(bufferedImage.getWidth() == 64, "Must be 64 pixels wide");
      Validate.validState(bufferedImage.getHeight() == 64, "Must be 64 pixels high");
      final ByteBuf byteBuf = Unpooled.buffer();
      final String icon;
      try {
        ImageIO.write(bufferedImage, "PNG", new ByteBufOutputStream(byteBuf));
        final ByteBuffer byteBuffer = Base64.getEncoder().encode(byteBuf.nioBuffer());
        icon = "data:image/png;base64," + StandardCharsets.UTF_8.decode(byteBuffer);
      } finally {
        byteBuf.release();
      }
      return icon;
    }

  }

}
