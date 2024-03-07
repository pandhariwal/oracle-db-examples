/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.jdbc.samples.springsharding.service;

import com.oracle.jdbc.samples.springsharding.model.Note;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
public class NoteServiceImplementation implements NoteService {
    private final JdbcTemplate catalogJdbcTemplate;
    private final JdbcTemplate directShardJdbcTemplate;
    private final RowMapper<Note> noteRowMapper;

    public NoteServiceImplementation(JdbcTemplate catalogJdbcTemplate,
                                     @Qualifier("directShardJdbcTemplate") JdbcTemplate directShardJdbcTemplate,
                                     RowMapper<Note> noteRowMapper) {
        this.catalogJdbcTemplate = catalogJdbcTemplate;
        this.directShardJdbcTemplate = directShardJdbcTemplate;
        this.noteRowMapper = noteRowMapper;
    }


    @Override
    public Note addNote(Note note) {
        note.setId(nextSequenceValue(directShardJdbcTemplate));

        directShardJdbcTemplate.update("INSERT INTO notes VALUES (?, ?, ?, ?)",
                note.getId(),
                note.getUserId(),
                note.getTitle(),
                note.getContent());

        return note;
    }

    @Override
    public void removeNote(Long noteId, Long userId) {
        directShardJdbcTemplate.update("DELETE FROM notes WHERE note_id = ? and user_id = ?", noteId, userId);
    }

    @Override
    public Note getNote(Long noteId, Long userId) {
        String query = "SELECT * FROM notes n WHERE n.user_id = ? AND n.note_id = ?";

        return directShardJdbcTemplate.queryForObject(query, noteRowMapper, userId, noteId);
    }

    @Override
    public void updateNote(Note note) {
        String updateQuery = "UPDATE notes " +
                             "SET title = ?, content = ? " +
                             "WHERE note_id = ? and user_id = ?";

        int affectedRows = directShardJdbcTemplate.update(updateQuery,
                                note.getTitle(),
                                note.getContent(),
                                note.getId(),
                                note.getUserId());

        if (affectedRows == 0) {
            throw new EmptyResultDataAccessException(1);
        }
    }

    @Override
    public List<Note> getNotesForUser(Long userId) {
        String query = "SELECT * FROM notes n WHERE n.user_id = ?";

        return directShardJdbcTemplate.query(query, noteRowMapper, userId);
    }

    @Override
    public List<Note> getAllNotes() {
        return catalogJdbcTemplate.query("SELECT * FROM notes n", noteRowMapper);
    }

    private Long nextSequenceValue(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject("SELECT note_sequence.nextval FROM DUAL", Long.class);
    }
}
