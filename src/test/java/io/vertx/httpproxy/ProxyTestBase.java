package io.vertx.httpproxy;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.io.Closeable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(VertxUnitRunner.class)
public class ProxyTestBase {

  private static final AtomicInteger NEXT_PORT = new AtomicInteger(25000);
  private static final String FRONTEND_PORT = "FRONTEND_PORT";
  private static final String BACKEND_PORT = "BACKEND_PORT";

  protected HttpServerOptions proxyOptions;
  protected HttpClientOptions clientOptions;


  protected Vertx vertx;

  @Before
  public void setUp(TestContext ctx) {
    int fePort = getNextPort();
    int bePort = getNextPort();
    ctx.put(FRONTEND_PORT, fePort);
    ctx.put(BACKEND_PORT, bePort);
    proxyOptions = new HttpServerOptions().setPort(fePort).setHost("localhost");
    clientOptions = new HttpClientOptions();
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  protected int getFrontendPort(TestContext ctx) {
    return ctx.get(FRONTEND_PORT);
  }

  protected int getBackendPort(TestContext ctx) {
    return ctx.get(BACKEND_PORT);
  }

  private static int getNextPort() {
    return NEXT_PORT.getAndIncrement();
  }

  protected Closeable startProxy(SocketAddress backend) {
    return startProxy(req -> Future.succeededFuture(backend));
  }

  protected Closeable startProxy(Function<HttpServerRequest, Future<SocketAddress>> selector) {
    CompletableFuture<Closeable> res = new CompletableFuture<>();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Future<Void> startFuture) {
        HttpClient proxyClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
        HttpServer proxyServer = vertx.createHttpServer(new HttpServerOptions(proxyOptions));
        HttpProxy proxy = HttpProxy.reverseProxy(proxyClient);
        proxy.cache(new SimpleHttpResponseCache());
        proxy.selector(selector);
        proxyServer.requestHandler(proxy);
        proxyServer.listen(ar -> startFuture.handle(ar.mapEmpty()));
      }
    }, ar -> {
      if (ar.succeeded()) {
        String id = ar.result();
        res.complete(() -> {
          CountDownLatch latch = new CountDownLatch(1);
          vertx.undeploy(id, ar2 -> latch.countDown());
          try {
            latch.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
          }
        });
      } else {
        res.completeExceptionally(ar.cause());
      }
    });
    try {
      return res.get(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new AssertionError(e.getMessage());
    } catch (TimeoutException e) {
      throw new AssertionError(e);
    }
  }

  protected void startHttpServer(TestContext ctx, HttpServerOptions options, Handler<HttpServerRequest> handler) {
    HttpServer proxyServer = vertx.createHttpServer(options);
    proxyServer.requestHandler(handler);
    Async async1 = ctx.async();
    proxyServer.listen(ctx.asyncAssertSuccess(p -> async1.complete()));
    async1.awaitSuccess();
  }

  protected SocketAddress startHttpBackend(TestContext ctx, int port, Handler<HttpServerRequest> handler) {
    return startHttpBackend(ctx, new HttpServerOptions().setPort(port).setHost("localhost"), handler);
  }

  protected SocketAddress startHttpBackend(TestContext ctx, HttpServerOptions options, Handler<HttpServerRequest> handler) {
    HttpServer backendServer = vertx.createHttpServer(options);
    backendServer.requestHandler(handler);
    Async async = ctx.async();
    backendServer.listen(ctx.asyncAssertSuccess(s -> async.complete()));
    async.awaitSuccess();
    return new SocketAddressImpl(options.getPort(), "localhost");
  }

  protected SocketAddress startNetBackend(TestContext ctx, int port, Handler<NetSocket> handler) {
    NetServer backendServer = vertx.createNetServer(new HttpServerOptions().setPort(port).setHost("localhost"));
    backendServer.connectHandler(handler);
    Async async = ctx.async();
    backendServer.listen(ctx.asyncAssertSuccess(s -> async.complete()));
    async.awaitSuccess();
    return new SocketAddressImpl(port, "localhost");
  }

  protected void run(TestContext ctx, Runnable runnable) {
    WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(this.getBackendPort(ctx)));
    wireMockRule.apply(new Statement() {
      @Override
      public void evaluate() {
        runnable.run();
      }
    }, null, null);
  }

}
