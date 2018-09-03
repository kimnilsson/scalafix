package scalafix.internal.interfaces

import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util
import metaconfig.Conf
import metaconfig.Configured
import scala.collection.JavaConverters._
import scala.meta.io.AbsolutePath
import scala.meta.io.Classpath
import scalafix.interfaces.ScalafixError
import scalafix.interfaces.ScalafixMainArgs
import scalafix.interfaces.ScalafixMainCallback
import scalafix.interfaces.ScalafixMainMode
import scalafix.interfaces.ScalafixRule
import scalafix.internal.config.ScalafixConfig
import scalafix.internal.v1.Args
import scalafix.internal.v1.MainOps
import scalafix.internal.v1.Rules

final case class ScalafixMainArgsImpl(args: Args = Args.default)
    extends ScalafixMainArgs {

  override def run(): Array[ScalafixError] = {
    val exit = MainOps.run(Array(), args)
    ScalafixErrorImpl.fromScala(exit)
  }

  override def withRules(rules: util.List[String]): ScalafixMainArgs =
    copy(args = args.copy(rules = rules.asScala.toList))

  override def withToolClasspath(
      classLoader: URLClassLoader): ScalafixMainArgs =
    copy(args = args.copy(toolClasspath = classLoader))

  override def withPaths(paths: util.List[Path]): ScalafixMainArgs =
    copy(
      args = args.copy(
        files = paths.asScala.iterator.map(AbsolutePath(_)(args.cwd)).toList)
    )

  override def withExcludedPaths(
      matchers: util.List[PathMatcher]): ScalafixMainArgs =
    copy(args = args.copy(exclude = matchers.asScala.toList))

  override def withWorkingDirectory(path: Path): ScalafixMainArgs = {
    require(path.isAbsolute, s"working directory must be relative: $path")
    copy(args = args.copy(cwd = AbsolutePath(path)))
  }

  override def withConfig(path: Path): ScalafixMainArgs =
    copy(args = args.copy(config = Some(AbsolutePath(path)(args.cwd))))

  override def withMode(mode: ScalafixMainMode): ScalafixMainArgs = mode match {
    case ScalafixMainMode.TEST =>
      copy(args = args.copy(test = true))
    case ScalafixMainMode.IN_PLACE =>
      copy(args = args.copy(stdout = false))
    case ScalafixMainMode.STDOUT =>
      copy(args = args.copy(stdout = true))
    case ScalafixMainMode.AUTO_SUPPRESS_LINTER_ERRORS =>
      copy(args = args.copy(autoSuppressLinterErrors = true))
  }

  override def withArgs(args: util.List[String]): ScalafixMainArgs = {
    val decoder = Args.decoder(this.args)
    val newArgs = Conf
      .parseCliArgs[Args](args.asScala.toList)
      .andThen(c => c.as[Args](decoder)) match {
      case Configured.Ok(value) =>
        value
      case Configured.NotOk(error) =>
        throw new IllegalArgumentException(error.toString())
    }
    copy(args = newArgs)
  }

  override def withPrintStream(out: PrintStream): ScalafixMainArgs =
    copy(args = args.copy(out = out))

  override def withClasspath(path: util.List[Path]): ScalafixMainArgs =
    copy(
      args = args.copy(
        classpath = Classpath(
          path.asScala.iterator.map(AbsolutePath(_)(args.cwd)).toList))
    )

  override def withSourceroot(path: Path): ScalafixMainArgs = {
    require(path.isAbsolute, s"sourceroot must be relative: $path")
    copy(args = args.copy(sourceroot = Some(AbsolutePath(path)(args.cwd))))
  }

  override def withMainCallback(
      callback: ScalafixMainCallback): ScalafixMainArgs =
    copy(args = args.copy(callback = callback))

  override def withCharset(charset: Charset): ScalafixMainArgs =
    copy(args = args.copy(charset = charset))

  override def availableRules(): util.List[ScalafixRule] = {
    Rules
      .all(args.toolClasspath)
      .map(rule => ScalafixRuleImpl(rule))
      .asJava
  }

  override def rulesThatWillRun(): util.List[ScalafixRule] = {
    val decoder = args.ruleDecoder(ScalafixConfig.default)
    val rules = decoder.read(args.rulesConf(() => args.fileConfig.get)).get
    rules.rules.map(rule => ScalafixRuleImpl(rule)).asJava
  }

  override def withScalacOptions(options: util.List[String]): ScalafixMainArgs =
    copy(args = args.copy(scalacOptions = options.asScala.toList))

  override def withScalaVersion(version: String): ScalafixMainArgs =
    copy(args = args.copy(scalaVersion = version))

}
