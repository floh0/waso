package com.github.floho.waso.entity

final case class Log(boss: String,
                     total: Int,
                     totalSuccess: Int,
                     records: Seq[LogRecord])
