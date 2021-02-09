package com.github.floho.waso.entity

final case class LogRecord(profession: String,
                           success: Int,
                           total: Int,
                           minDps: Option[Double],
                           avgDps: Option[Double],
                           maxDps: Option[Double])