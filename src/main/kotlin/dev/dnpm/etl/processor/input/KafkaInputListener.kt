/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2024  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.dnpm.etl.processor.input

import de.ukw.ccc.bwhc.dto.MtbFile
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.ApplicationEventPublisher
import org.springframework.kafka.listener.MessageListener

class KafkaInputListener(
    private val applicationEventPublisher: ApplicationEventPublisher
) : MessageListener<String, MtbFile> {
    override fun onMessage(data: ConsumerRecord<String, MtbFile>) {
        TODO("Not yet implemented")
    }
}