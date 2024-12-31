/*
 * Copyright © 2020 CodeOnce Software (https://www.codeonce.fr/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.codeonce.grizzly.runtime.util;

import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ObjectRowMapper implements RowMapper {
    private Class className;

    public ObjectRowMapper(Class className) {
        this.className = className;
    }

    public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object nt = new Object();
        Field[] fields = className.getDeclaredFields();
        try {
            nt = className.getDeclaredConstructor().newInstance();
            for (Field field : fields) {
                // If the field is not changed in the results, skip
                try {
                    rs.findColumn(field.getName());
                } catch (Exception e) {
                    continue;
                }
                // Modify the permissions of the corresponding filed
                boolean accessFlag = field.isAccessible();
                field.setAccessible(true);
                String value = rs.getString(field.getName());
                value = value == null ? "" : value;
                setFieldValue(nt, field, value);

                // Restore the permissions of the corresponding field
                field.setAccessible(accessFlag);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return nt;
    }

    /*
     * Assign values ​​to specific object properties based on type
     */
    public static void setFieldValue(Object form, Field field, String value) {

        String elemType = field.getType().toString();

        if (elemType.indexOf("boolean") != -1 || elemType.indexOf("Boolean") != -1) {
            try {
                field.set(form, Boolean.valueOf(value));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else if (elemType.indexOf("byte") != -1 || elemType.indexOf("Byte") != -1) {
            try {
                field.set(form, Byte.valueOf(value));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else if (elemType.indexOf("char") != -1 || elemType.indexOf("Character") != -1) {
            try {
                field.set(form, Character.valueOf(value.charAt(0)));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else if (elemType.indexOf("double") != -1 || elemType.indexOf("Double") != -1) {
            try {
                field.set(form, Double.valueOf(value));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else if (elemType.indexOf("float") != -1 || elemType.indexOf("Float") != -1) {
            try {
                field.set(form, Float.valueOf(value));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else if (elemType.indexOf("int") != -1 || elemType.indexOf("Integer") != -1) {
            try {
                field.set(form, Integer.valueOf(value));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else if (elemType.indexOf("long") != -1 || elemType.indexOf("Long") != -1) {
            try {
                field.set(form, Long.valueOf(value));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else if (elemType.indexOf("short") != -1 || elemType.indexOf("Short") != -1) {
            try {
                field.set(form, Short.valueOf(value));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else {
            try {
                field.set(form, (Object) value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
}