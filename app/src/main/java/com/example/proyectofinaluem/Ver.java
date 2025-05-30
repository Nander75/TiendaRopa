package com.example.proyectofinaluem;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.IOException;
import java.util.ArrayList;

public class Ver extends AppCompatActivity {

    private RecyclerView recyclerProductos;
    private ProductoAdapter adapter;
    private ArrayList<Producto> listaProductos;
    private DatabaseReference productosRef;
    private Button btnSalir, btnEscanearQR, btnUsarImagen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ver);

        recyclerProductos = findViewById(R.id.recyclerProductos);
        recyclerProductos.setLayoutManager(new LinearLayoutManager(this));

        listaProductos = new ArrayList<>();
        adapter = new ProductoAdapter(this, listaProductos);
        recyclerProductos.setAdapter(adapter);

        productosRef = FirebaseDatabase.getInstance().getReference("producto");

        btnSalir = findViewById(R.id.btnSalir);
        btnEscanearQR = findViewById(R.id.btnEscanearQR);
        btnUsarImagen = findViewById(R.id.btnUsarImagen);

        btnSalir.setOnClickListener(v -> {
            startActivity(new Intent(Ver.this, Menu.class));
            finish();
        });

        btnEscanearQR.setOnClickListener(v -> {
            IntentIntegrator integrator = new IntentIntegrator(Ver.this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            integrator.setPrompt("Escanea el código QR del producto");
            integrator.setCameraId(0);
            integrator.setBeepEnabled(true);
            integrator.setBarcodeImageEnabled(true);
            integrator.setCaptureActivity(CaptureActivityPortrait.class);
            integrator.initiateScan();
        });

        btnUsarImagen.setOnClickListener(v -> tomarFoto());

        cargarProductos(null); // Carga inicial
    }

    private void tomarFoto() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 102);
        } else {
            abrirCamara();
        }
    }

    private void abrirCamara() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, 101);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Resultado de escaneo QR
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            String idProducto = result.getContents();

            productosRef.child(idProducto).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        Producto producto = snapshot.getValue(Producto.class);
                        if (producto != null) {
                            Toast.makeText(Ver.this, "Producto detectado por QR: " + producto.getNombre(), Toast.LENGTH_SHORT).show();
                            cargarProductos(producto.getNombre());
                        } else {
                            Toast.makeText(Ver.this, "Producto no válido", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(Ver.this, "Producto no encontrado con ese QR", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(Ver.this, "Error al acceder a la base de datos", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        // Resultado de la cámara
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            Bitmap foto = (Bitmap) data.getExtras().get("data");

            try {
                ImageClassifier classifier = new ImageClassifier(this);
                String nombreDetectado = classifier.classify(foto);

                if (nombreDetectado != null) {
                    Toast.makeText(this, "Producto detectado: " + nombreDetectado, Toast.LENGTH_SHORT).show();
                    cargarProductos(nombreDetectado);
                } else {
                    Toast.makeText(this, "No se pudo identificar el producto (confianza baja)", Toast.LENGTH_SHORT).show();
                }

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error al cargar el modelo de IA", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void cargarProductos(String nombreAResaltar) {
        productosRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listaProductos.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Producto producto = data.getValue(Producto.class);
                    listaProductos.add(producto);
                }
                adapter.notifyDataSetChanged();

                if (nombreAResaltar != null) {
                    for (int i = 0; i < listaProductos.size(); i++) {
                        if (listaProductos.get(i).getNombre().equalsIgnoreCase(nombreAResaltar)) {
                            adapter.resaltarProducto(nombreAResaltar);
                            recyclerProductos.scrollToPosition(i);
                            return;
                        }
                    }
                } else {
                    adapter.resaltarProducto(null); // sin resaltado
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(Ver.this, "Error al cargar productos", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
