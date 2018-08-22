/****************************************************************************
**
** Copyright (C) 2017 The Qt Company Ltd.
** Contact: https://www.qt.io/licensing/
**
** This file is part of Qt Creator.
**
** Commercial License Usage
** Licensees holding valid commercial Qt licenses may use this file in
** accordance with the commercial license agreement provided with the
** Software or, alternatively, in accordance with the terms contained in
** a written agreement between you and The Qt Company. For licensing terms
** and conditions see https://www.qt.io/terms-conditions. For further
** information use the contact form at https://www.qt.io/contact-us.
**
** GNU General Public License Usage
** Alternatively, this file may be used under the terms of the GNU
** General Public License version 3 as published by the Free Software
** Foundation with exceptions as appearing in the file LICENSE.GPL3-EXCEPT
** included in the packaging of this file. Please review the following
** information to ensure the GNU General Public License requirements will
** be met: https://www.gnu.org/licenses/gpl-3.0.html.
**
****************************************************************************/

#include "qtsystemexceptionhandler.h"

//#include <utils/fileutils.h>
//#include <utils/hostosinfo.h>

#include <QCoreApplication>
#include <QDebug>
#include <QDir>
#include <QProcess>

#if defined(Q_OS_LINUX)
#include "client/linux/handler/exception_handler.h"
#elif defined(Q_OS_WIN)
#include "client/windows/handler/exception_handler.h"
#elif defined(Q_OS_MACOS)
#include "client/mac/handler/exception_handler.h"
#endif

#if defined(Q_OS_LINUX)
static bool
exceptionHandlerCallback(const google_breakpad::MinidumpDescriptor &descriptor,
                         void * /*context*/, bool succeeded) {
  qDebug() << "exceptionHandlerCallback started";

  if (!succeeded)
    return succeeded;

  qDebug() << "exceptionHandlerCallback passed";

  const QStringList argumentList = {
      QString::fromLocal8Bit(descriptor.path()),
      QString::number(QtSystemExceptionHandler::startTime().toTime_t()),
      QCoreApplication::applicationName(),
      QCoreApplication::applicationVersion(),
      QtSystemExceptionHandler::plugins(),
      QtSystemExceptionHandler::buildVersion(),
      QCoreApplication::applicationFilePath()};

  qWarning() << "[execute crash handler]"
             << QtSystemExceptionHandler::crashHandlerPath();
  qWarning() << argumentList;
  return !QProcess::execute(QtSystemExceptionHandler::crashHandlerPath(),
                            argumentList);
}
#elif defined(Q_OS_MACOS)
static bool exceptionHandlerCallback(const char *dump_dir,
                                     const char *minidump_id, void *context,
                                     bool succeeded) {
  Q_UNUSED(context);

  if (!succeeded)
    return succeeded;

  const QString path = QString::fromLocal8Bit(dump_dir) + '/' +
                       QString::fromLocal8Bit(minidump_id) + ".dmp";
  const QStringList argumentList = {
      path,
      QString::number(QtSystemExceptionHandler::startTime().toTime_t()),
      QCoreApplication::applicationName(),
      QCoreApplication::applicationVersion(),
      QtSystemExceptionHandler::plugins(),
      QtSystemExceptionHandler::buildVersion(),
      QCoreApplication::applicationFilePath()};

  qWarning() << "[execute crash handler]"
             << QtSystemExceptionHandler::crashHandlerPath();
  qWarning() << argumentList;
  QProcess::startDetached(QtSystemExceptionHandler::crashHandlerPath(),
                            argumentList);

  return true;
}
#elif defined(Q_OS_WIN)
static bool exceptionHandlerCallback(const wchar_t *dump_path,
                                     const wchar_t *minidump_id, void *context,
                                     EXCEPTION_POINTERS *exinfo,
                                     MDRawAssertionInfo *assertion,
                                     bool succeeded) {
  Q_UNUSED(assertion);
  Q_UNUSED(exinfo);
  Q_UNUSED(context);

  if (!succeeded)
    return succeeded;

  const QString path =
      QString::fromWCharArray(dump_path, int(wcslen(dump_path))) + '/' +
      QString::fromWCharArray(minidump_id, int(wcslen(minidump_id))) + ".dmp";

  const QStringList argumentList = {
      path,
      QString::number(QtSystemExceptionHandler::startTime().toTime_t()),
      QCoreApplication::applicationName(),
      QCoreApplication::applicationVersion(),
      QtSystemExceptionHandler::plugins(),
      QtSystemExceptionHandler::buildVersion(),
      QCoreApplication::applicationFilePath()};

  qWarning() << "[execute crash handler]"
             << QtSystemExceptionHandler::crashHandlerPath();
  qWarning() << argumentList;
  return !QProcess::execute(QtSystemExceptionHandler::crashHandlerPath(),
                            argumentList);
}
#endif

static QDir getAppTempDir() {
  QDir temp_dir = QDir::tempPath();
  QString org_name = QCoreApplication::organizationName();
  QString app_name = QCoreApplication::applicationName();

  if (!org_name.isEmpty() && !app_name.isEmpty())
    temp_dir = temp_dir.filePath(org_name + "." + app_name);
  else if (!org_name.isEmpty())
    temp_dir = temp_dir.filePath(org_name);
  else if (!app_name.isEmpty())
    temp_dir = temp_dir.filePath(app_name);

  temp_dir.mkdir(".");
  return temp_dir;
}

static QDateTime s_startTime;
static QString s_plugins;
static QString s_buildVersion;
static QString s_crashHandlerPath;
google_breakpad::ExceptionHandler* g_exceptionHandler = nullptr;

#if defined(Q_OS_LINUX)
QtSystemExceptionHandler::QtSystemExceptionHandler(const QString &libexecPath)
    : exceptionHandler(new google_breakpad::ExceptionHandler(
          google_breakpad::MinidumpDescriptor(QDir::tempPath().toStdString()),
          NULL, exceptionHandlerCallback, NULL, true, -1)) {
  init(libexecPath);
}
#elif defined(Q_OS_MACOS)
bool filterCallback(void *context) {
    qDebug() << "!!! filterCallback is called";

    // Use custom System crash handler on MacOS
    return true;
}

bool directCallback( void *context,
                                  int exception_type,
                                  int exception_code,
                                  int exception_subcode,
                     mach_port_t thread_name) {
    qDebug() << "!!! Direct callback is called";
    QProcess::startDetached(QtSystemExceptionHandler::crashHandlerPath());
    //g_exceptionHandler->Teardown();
    //g_exceptionHandler = nullptr;
    return true;
}

QtSystemExceptionHandler::QtSystemExceptionHandler(const QString &libexecPath)
    : exceptionHandler(new google_breakpad::ExceptionHandler(
          QDir::tempPath().toStdString(), filterCallback, exceptionHandlerCallback, NULL,
          true, NULL)) {
   // : exceptionHandler(new google_breakpad::ExceptionHandler(
   //           directCallback, NULL, true)) {
  g_exceptionHandler = exceptionHandler;
  init(libexecPath);
}
#elif defined(Q_OS_WIN)
QtSystemExceptionHandler::QtSystemExceptionHandler(const QString &libexecPath)
    : exceptionHandler(new google_breakpad::ExceptionHandler(
          getAppTempDir().absolutePath().toStdWString(), NULL,
          exceptionHandlerCallback, NULL,
          google_breakpad::ExceptionHandler::HANDLER_ALL)) {
  init(libexecPath);
}
#else
QtSystemExceptionHandler::QtSystemExceptionHandler(
    const QString & /*libexecPath*/)
    : exceptionHandler(0) {}
#endif

void QtSystemExceptionHandler::init(const QString &libexecPath) {
  s_startTime = QDateTime::currentDateTime();

  s_crashHandlerPath = libexecPath + "/reportApp";

#ifdef Q_OS_WIN
  s_crashHandlerPath = libexecPath + "/reportApp.exe";
#endif

#ifdef Q_OS_MACOS
  s_crashHandlerPath = libexecPath + "/reportApp";
#endif
}

QtSystemExceptionHandler::~QtSystemExceptionHandler() {
#ifdef ENABLE_QT_BREAKPAD
  delete exceptionHandler;
#endif
}

void QtSystemExceptionHandler::setPlugins(const QStringList &pluginNameList) {
  s_plugins = QString("{%1}").arg(pluginNameList.join(","));
}

void QtSystemExceptionHandler::setBuildVersion(const QString &version) {
  s_buildVersion = version;
}

QString QtSystemExceptionHandler::buildVersion() { return s_buildVersion; }

QString QtSystemExceptionHandler::plugins() { return s_plugins; }

void QtSystemExceptionHandler::setCrashHandlerPath(
    const QString &crashHandlerPath) {
  s_crashHandlerPath = crashHandlerPath;
}

QString QtSystemExceptionHandler::crashHandlerPath() {
  return s_crashHandlerPath;
}

void QtSystemExceptionHandler::crash() {
  int *a = (int *)0x42;

  fprintf(stdout, "Going to crash...\n");
  fprintf(stdout, "A = %d", *a);
}

QDateTime QtSystemExceptionHandler::startTime() { return s_startTime; }