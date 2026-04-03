/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.todolist.*;

import java.util.List;

public class TestTodolist {
  public static void main(String[] args) throws Exception {
    try (var todo = new TodoList()) {
      // Empty list should throw
      try {
        todo.getLast();
        throw new RuntimeException("Should have thrown EmptyTodoList");
      } catch (TodoException.EmptyTodoList e) {
        // expected
      }

      // Empty string should throw
      try {
        Todolist.createEntryWith("");
        throw new RuntimeException("Should have thrown EmptyString");
      } catch (TodoException.EmptyString e) {
        // expected
      }

      todo.addItem("Write strings support");
      assert todo.getLast().equals("Write strings support");

      todo.addItem("Write tests for strings support");
      assert todo.getLast().equals("Write tests for strings support");

      TodoEntry entry = Todolist.createEntryWith("Write bindings for strings as record members");
      todo.addEntry(entry);
      assert todo.getLast().equals("Write bindings for strings as record members");
      assert todo.getLastEntry().text().equals("Write bindings for strings as record members");

      // Unicode handling
      todo.addItem("Test unicode handling without an entry");
      assert todo.getLast().equals("Test unicode handling without an entry");

      TodoEntry entry2 = new TodoEntry("Test unicode handling in an entry");
      todo.addEntry(entry2);
      assert todo.getLastEntry().text().equals("Test unicode handling in an entry");

      assert todo.getEntries().size() == 5;

      todo.addEntries(List.of(new TodoEntry("foo"), new TodoEntry("bar")));
      assert todo.getEntries().size() == 7;
      assert todo.getLastEntry().text().equals("bar");

      todo.addItems(List.of("bobo", "fofo"));
      assert todo.getItems().size() == 9;
      assert todo.getItems().get(7).equals("bobo");

      assert Todolist.getDefaultList() == null;

      // Test global default list
      try (var todo2 = new TodoList()) {
        Todolist.setDefaultList(todo);
        try (var defaultList = Todolist.getDefaultList()) {
          assert defaultList != null;
          assert defaultList.getLast().equals("fofo");
        }

        todo2.makeDefault();
        try (var defaultList = Todolist.getDefaultList()) {
          assert defaultList != null;
          assert defaultList.getItems().isEmpty();
        }
      }

      // Duplicate detection
      try (var todo3 = new TodoList()) {
        todo3.addItem("foo");
        try {
          todo3.addItem("foo");
          throw new RuntimeException("Should have thrown DuplicateTodo");
        } catch (TodoException.DuplicateTodo e) {
          // expected
        }
      }
    }
  }
}
